package com.easyhooon.dari.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when a `dari { }` mapping names a class or method that is not in the compiled classes.
 * Without it, a typo or a rename still builds and the mapping silently records nothing.
 */
@CacheableTask
abstract class DariCheckMappingsTask : DefaultTask() {

    @get:Input
    abstract val mappings: ListProperty<CheckedMapping>

    @get:Input
    abstract val failOnUnmatchedMapping: Property<Boolean>

    /** True when the classes include dependencies (`scope = ALL`); only changes the wording. */
    @get:Input
    abstract val includesDependencies: Property<Boolean>

    /** True in a library module, whose dependencies are never instrumented; only changes the wording. */
    @get:Input
    abstract val inLibrary: Property<Boolean>

    @get:Classpath
    abstract val jars: ListProperty<RegularFile>

    @get:Classpath
    abstract val dirs: ListProperty<Directory>

    /** Lists the unmatched mappings, or is empty; it exists so the task can be up to date and cached. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val problems = findUnmatchedMappings(
            mappings = mappings.get(),
            dirs = dirs.get().map { it.asFile },
            jars = jars.get().map { it.asFile },
            includesDependencies = includesDependencies.get(),
            inLibrary = inLibrary.get(),
        )
        report.get().asFile.writeText(problems.joinToString("\n", postfix = if (problems.isEmpty()) "" else "\n"))
        if (problems.isEmpty()) return
        if (failOnUnmatchedMapping.get()) {
            val message = problems.joinToString(
                separator = "\n",
                prefix = "Dari: ${problems.size} mapping(s) match no code, so they would record nothing:\n",
                postfix = "\nFix the class or method name, or set dari { failOnUnmatchedMapping = false } to only warn.",
            ) { "- $it" }
            throw GradleException(message)
        }
        problems.forEach { logger.warn("Dari: $it") }
    }
}
