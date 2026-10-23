package com.easyhooon.dari.gradle

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.Serializable
import java.util.zip.ZipFile

private const val MAX_SUGGESTIONS = 3
private const val MAX_LISTED_METHODS = 10

/** A mapping as the build-time check sees it: which `dari { }` call, and the class and method it names. */
data class CheckedMapping(val dslName: String, val className: String, val methodName: String) : Serializable {
    val where: String get() = "dari { $dslName(\"$className\", \"$methodName\") }"

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** Nested classes match with `.` or `$`, the same way for the check and the transform. */
internal fun normalizeClassName(name: String): String = name.replace('$', '.')

/**
 * Returns one message per mapping that would instrument nothing: its class is not among [dirs] and [jars],
 * or the class has no method of that name the transform would rewrite.
 */
internal fun findUnmatchedMappings(
    mappings: List<CheckedMapping>,
    dirs: List<File>,
    jars: List<File>,
    includesDependencies: Boolean,
    inLibrary: Boolean = false,
): List<String> {
    val classes = ClassFiles(dirs, jars)
    val methodsByClass = mutableMapOf<String, List<MethodInfo>>()
    return mappings.mapNotNull { mapping ->
        val name = normalizeClassName(mapping.className)
        val file = classes.byName[name] ?: return@mapNotNull missingClass(mapping, name, classes, includesDependencies, inLibrary)
        val methods = methodsByClass.getOrPut(name) { readMethods(file.read()) }
        missingMethod(mapping, methods)
    }
}

private fun missingClass(
    mapping: CheckedMapping,
    name: String,
    classes: ClassFiles,
    includesDependencies: Boolean,
    inLibrary: Boolean,
): String {
    val parent = name.substringBeforeLast('.', "")
    val simple = name.substringAfterLast('.')
    val siblings = classes.byName.keys.filter { it.substringBeforeLast('.', "") == parent }
    val similar = suggest(simple, siblings) { it.substringAfterLast('.') }
        .ifEmpty { classes.byName.keys.filter { it.substringAfterLast('.').equals(simple, ignoreCase = true) }.sorted().take(MAX_SUGGESTIONS) }
    val where = if (includesDependencies) "this module or its dependencies" else "this module"
    val otherModule = " A class in another module needs the plugin applied in that module;"
    val hint = when {
        includesDependencies -> ""
        inLibrary -> "$otherModule a library module never instruments its dependencies, so map those in the app module with scope = ALL."
        else -> "$otherModule one in a library needs scope = InstrumentationScope.ALL."
    }
    return "${mapping.where}: no class ${mapping.className} in $where.${didYouMean(similar)}$hint"
}

private fun missingMethod(mapping: CheckedMapping, methods: List<MethodInfo>): String? {
    val sameName = methods.filter { it.name == mapping.methodName }
    if (sameName.any { it.isInstrumentable }) return null
    if (sameName.any { !it.hasBody }) {
        return "${mapping.where}: ${mapping.className}.${mapping.methodName} has no body (abstract or native); map the class that implements it."
    }
    val candidates = methods.filter { it.isInstrumentable && it.isNamedInSource }.map { it.name }.distinct()
    // Kotlin mangles internal functions to `name$module`; compare the part a developer typed.
    val similar = suggest(mapping.methodName, candidates) { it.substringBefore('$') }
    val listed = if (similar.isNotEmpty()) {
        didYouMean(similar)
    } else if (candidates.isNotEmpty()) {
        val shown = candidates.sorted().take(MAX_LISTED_METHODS)
        val more = if (candidates.size > shown.size) ", …" else ""
        " Its methods: [${shown.joinToString()}$more]."
    } else {
        ""
    }
    return "${mapping.where}: ${mapping.className} has no method '${mapping.methodName}' with a body.$listed"
}

private fun didYouMean(names: List<String>): String = if (names.isEmpty()) "" else " Did you mean ${names.joinToString(" or ")}?"

/** Up to [MAX_SUGGESTIONS] [candidates] whose [key] is a few edits from [typed], closest first. */
private fun suggest(typed: String, candidates: Collection<String>, key: (String) -> String): List<String> {
    val limit = maxOf(2, typed.length / 3)
    return candidates
        .map { it to editDistance(typed.lowercase(), key(it).lowercase()) }
        .filter { (_, distance) -> distance <= limit }
        .sortedWith(compareBy({ it.second }, { it.first }))
        .map { it.first }
        .take(MAX_SUGGESTIONS)
}

private fun editDistance(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        previous = current
    }
    return previous[b.length]
}

private class MethodInfo(val name: String, access: Int) {
    val hasBody = access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE) == 0

    /** Same rule as `DariClassVisitor.visitMethod`: a body, and not a synthetic forwarder such as `$default`. */
    val isInstrumentable = hasBody && access and Opcodes.ACC_SYNTHETIC == 0

    /** Leaves out constructors and compiler-made helpers like Kotlin's `foo$lambda$0` and `access$foo`. */
    val isNamedInSource = !name.startsWith('<') && "\$lambda" !in name && !name.startsWith("access\$")
}

private fun readMethods(bytes: ByteArray): List<MethodInfo> {
    val methods = mutableListOf<MethodInfo>()
    val visitor = object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor? {
            methods += MethodInfo(name, access)
            return null
        }
    }
    ClassReader(bytes).accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return methods
}

/** Class files by normalized name. Only names are listed up front; bytes are read for mapped classes alone. */
private class ClassFiles(dirs: List<File>, jars: List<File>) {
    class Entry(val read: () -> ByteArray)

    val byName = mutableMapOf<String, Entry>()

    init {
        dirs.forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                add(file.relativeTo(dir).invariantSeparatorsPath) { file.readBytes() }
            }
        }
        jars.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.forEach { name ->
                    add(name) { ZipFile(jar).use { it.getInputStream(it.getEntry(name)).readBytes() } }
                }
            }
        }
    }

    private fun add(path: String, read: () -> ByteArray) {
        if (!path.endsWith(".class") || path.startsWith("META-INF/")) return
        val binaryName = path.removeSuffix(".class").replace('/', '.')
        if (binaryName.endsWith("module-info") || binaryName.endsWith("package-info")) return
        byName.putIfAbsent(normalizeClassName(binaryName), Entry(read))
    }
}
