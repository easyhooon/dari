package com.easyhooon.dari.gradle

import android.webkit.WebView
import com.easyhooon.dari.auto.DariHooks
import fixtures.MyWebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class DariClassVisitorTest {

    private val warnings = mutableListOf<String>()
    private val log = object : DariLog {
        override fun info(message: String) = Unit

        override fun warn(message: String) {
            warnings += message
        }
    }

    private val targets = listOf(
        MappingTarget("fixtures.Bridge", "onRequest", "REQ"),
        MappingTarget("fixtures.Bridge", "send", "SEND"),
        MappingTarget("fixtures.Bridge", "earlyReturn", "EARLY"),
        MappingTarget("fixtures.Outer.Inner", "onRequest", "INNER"),
        MappingTarget("fixtures.ManualBridge", "onRequest", "MANUAL"),
        MappingTarget("fixtures.Bridge", "missing", "MISSING"),
    )

    private val loader = TransformingLoader()

    @Before
    fun setup() = DariHooks.calls.clear()

    @Test
    fun `mapped method reports spec, receiver and args, then runs its body`() {
        val bridge = loader.new("fixtures.Bridge")
        bridge.call("onRequest", "getAppInfo", "req_1", null)

        assertEquals(listOf("mapped REQ Bridge [getAppInfo, req_1, null]"), DariHooks.calls)
        assertEquals("getAppInfo", bridge.javaClass.getField("last").get(bridge))
    }

    @Test
    fun `static methods pass a null receiver and boxed primitives`() {
        val bridge = loader.loadClass("fixtures.Bridge")
        bridge.methods.single { it.name == "send" }.invoke(null, "h", true, 7, 9L)

        assertEquals(listOf("mapped SEND null [h, true, 7, 9]"), DariHooks.calls)
    }

    @Test
    fun `return values are untouched on every path`() {
        val bridge = loader.new("fixtures.Bridge")

        assertEquals(0, bridge.call("earlyReturn", null))
        assertEquals(3, bridge.call("earlyReturn", "abc"))
        assertEquals(2, DariHooks.calls.size)
    }

    @Test
    fun `nested classes match with a dot instead of a dollar sign`() {
        loader.new("fixtures.Outer\$Inner").call("onRequest", "x")

        assertEquals(listOf("mapped INNER Inner [x]"), DariHooks.calls)
    }

    @Test
    fun `a method that already calls DariInterceptor is skipped with a warning`() {
        loader.new("fixtures.ManualBridge").call("onRequest", "x")

        assertEquals(emptyList<String>(), DariHooks.calls)
        assertTrue(warnings.any { "fixtures.ManualBridge.onRequest already calls DariInterceptor" in it })
    }

    @Test
    fun `a mapping whose method does not exist warns`() {
        loader.loadClass("fixtures.Bridge")

        assertTrue(warnings.any { "fixtures.Bridge has no method 'missing'" in it })
    }

    @Test
    fun `addJavascriptInterface goes through the hook for WebView and subclasses`() {
        val webView = WebView()
        val subclass = loader.loadClass("fixtures.MyWebView").getConstructor().newInstance() as WebView
        val screen = loader.new("fixtures.Screen")
        screen.javaClass.getMethod("setup", WebView::class.java, Any::class.java).invoke(screen, webView, Any())
        screen.javaClass.methods.single { it.name == "setupSubclass" }.invoke(screen, subclass)

        assertEquals(listOf("add Android", "add Sub"), DariHooks.calls)
        assertEquals(listOf("Android"), webView.added)
        assertEquals(listOf("Sub"), subclass.added)
    }

    @Test
    fun `super calls inside a WebView subclass stay direct`() {
        val subclass = loader.loadClass("fixtures.MyWebView").getConstructor().newInstance() as WebView
        subclass.javaClass.getMethod("addSelf").invoke(subclass)

        assertEquals(emptyList<String>(), DariHooks.calls)
        assertEquals(listOf("Self"), subclass.added)
    }

    @Test
    fun `WebViewClient doUpdateVisitedHistory reports the page URL`() {
        val client = loader.new("fixtures.Client")
        client.javaClass.getMethod("doUpdateVisitedHistory", WebView::class.java, String::class.java, Boolean::class.java)
            .invoke(client, WebView(), "https://example.com", false)

        assertEquals(listOf("url https://example.com"), DariHooks.calls)
        assertEquals("https://example.com", client.javaClass.getField("seen").get(client))
    }

    private fun Any.call(name: String, vararg args: Any?): Any? = javaClass.methods.single { it.name == name }.invoke(this, *args)

    private fun ClassLoader.new(name: String): Any = loadClass(name).getConstructor().newInstance()

    /** Loads `fixtures.*` from transformed bytes; stubs and the recording hooks come from the test classpath. */
    private inner class TransformingLoader : ClassLoader(DariClassVisitorTest::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("fixtures.")) return super.loadClass(name, resolve)
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)?.let { return it }
                val bytes = transform(name, parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.readBytes())
                return defineClass(name, bytes, 0, bytes.size)
            }
        }
    }

    private fun transform(name: String, bytes: ByteArray): ByteArray {
        // The defining class loader verifies the result, like D8 does in a real build.
        val writer = object : ClassWriter(COMPUTE_FRAMES) {
            override fun getCommonSuperClass(type1: String, type2: String) = "java/lang/Object"
        }
        val visitor = DariClassVisitor(
            api = Opcodes.ASM9,
            next = writer,
            className = name,
            targets = targets,
            isWebViewClient = name == "fixtures.Client",
            isWebView = { it == WEB_VIEW || it == MyWebView::class.java.name.replace('.', '/') },
            log = log,
        )
        ClassReader(bytes).accept(visitor, ClassReader.EXPAND_FRAMES)
        return writer.toByteArray()
    }
}
