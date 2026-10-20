package com.easyhooon.dari.gradle

import fixtures.Bridge
import fixtures.SuspendBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MappingCheckTest {

    @get:Rule
    val temp = TemporaryFolder()

    // Java and Kotlin fixtures compile to separate directories, like an app's javac and Kotlin outputs.
    private val javaClasses = classesDir(Bridge::class.java)
    private val kotlinClasses = classesDir(SuspendBridge::class.java)

    @Test
    fun `mappings that name existing methods pass`() {
        val problems = check(
            mapping("fixtures.Bridge", "onRequest"),
            mapping("fixtures.Bridge", "send"),
            mapping("fixtures.Outer.Inner", "onRequest"),
            mapping("fixtures.Outer\$Inner", "onRequest"),
            mapping("fixtures.SuspendBridge", "send"),
        )

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `a missing class names the mapping and similar classes in its package`() {
        val problem = check(mapping("fixtures.Brdge", "onRequest", "webToAppResponse")).single()

        val expected = """dari { webToAppResponse("fixtures.Brdge", "onRequest") }: no class fixtures.Brdge in this module."""
        assertTrue(problem, problem.startsWith(expected))
        assertTrue(problem, "Did you mean fixtures.Bridge?" in problem)
        assertTrue(problem, "needs scope = InstrumentationScope.ALL" in problem)
    }

    @Test
    fun `a class in the wrong package suggests the same simple name elsewhere`() {
        val problem = check(mapping("com.example.Bridge", "onRequest")).single()

        assertTrue(problem, "Did you mean fixtures.Bridge?" in problem)
    }

    @Test
    fun `with dependencies included the message drops the scope hint`() {
        val problem = findUnmatchedMappings(listOf(mapping("fixtures.Nope", "x")), listOf(javaClasses), emptyList(), true).single()

        assertTrue(problem, "no class fixtures.Nope in this module or its dependencies." in problem)
        assertTrue(problem, "InstrumentationScope" !in problem)
    }

    @Test
    fun `in a library module the hint points to the app module instead of scope`() {
        val problem = findUnmatchedMappings(listOf(mapping("fixtures.Nope", "x")), listOf(javaClasses), emptyList(), false, inLibrary = true).single()

        assertTrue(problem, "a library module never instruments its dependencies" in problem)
        assertTrue(problem, "needs scope = InstrumentationScope.ALL" !in problem)
    }

    @Test
    fun `a misspelled nested class suggests the nested class`() {
        val problem = check(mapping("fixtures.Outer\$Innr", "onRequest")).single()

        assertTrue(problem, "no class fixtures.Outer\$Innr" in problem)
        assertTrue(problem, "Did you mean fixtures.Outer.Inner?" in problem)
    }

    @Test
    fun `a missing method suggests similar method names`() {
        val problem = check(mapping("fixtures.Bridge", "onRequst")).single()

        assertTrue(problem, "fixtures.Bridge has no method 'onRequst' with a body. Did you mean onRequest?" in problem)
    }

    @Test
    fun `a missing method with no similar name lists the class's methods`() {
        val problem = check(mapping("fixtures.Bridge", "handle")).single()

        assertTrue(problem, "Its methods: [earlyReturn, onRequest, send]." in problem)
    }

    @Test
    fun `a Kotlin class is checked like a Java one`() {
        val problem = check(mapping("fixtures.SuspendBridge", "sned")).single()

        assertTrue(problem, "fixtures.SuspendBridge has no method 'sned'" in problem)
        assertTrue(problem, "Did you mean send?" in problem)
    }

    @Test
    fun `an abstract method does not count, since the transform needs a body`() {
        val problem = check(mapping("fixtures.BridgeApi", "onRequest")).single()

        assertTrue(problem, "fixtures.BridgeApi.onRequest has no body (abstract or native)" in problem)
    }

    @Test
    fun `classes inside jars are found too`() {
        val jar = temp.newFile("classes.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            listOf("fixtures/Bridge.class", "fixtures/Outer\$Inner.class").forEach { path ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(javaClasses.resolve(path).readBytes())
                zip.closeEntry()
            }
        }
        val problems = findUnmatchedMappings(
            listOf(mapping("fixtures.Bridge", "onRequest"), mapping("fixtures.Outer.Inner", "onRequest"), mapping("fixtures.Bridge", "onRequst")),
            dirs = emptyList(),
            jars = listOf(jar),
            includesDependencies = true,
        )

        assertEquals(1, problems.size)
        assertTrue(problems.single(), "Did you mean onRequest?" in problems.single())
    }

    private fun check(vararg mappings: CheckedMapping): List<String> =
        findUnmatchedMappings(mappings.toList(), listOf(javaClasses, kotlinClasses), emptyList(), includesDependencies = false)

    private fun mapping(className: String, methodName: String, dslName: String = "webToAppRequest") =
        CheckedMapping(dslName, className, methodName)

    private fun classesDir(type: Class<*>): File = File(type.protectionDomain.codeSource.location.toURI())
}
