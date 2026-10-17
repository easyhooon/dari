package com.easyhooon.dari.export

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DariExporterAndroidTest {

    // Share flows run from DariActivity, so use an Activity context like production.
    private lateinit var context: Activity
    private lateinit var exportDir: File

    @Before
    fun setup() {
        clearFileProviderCache()
        context = Robolectric.buildActivity(Activity::class.java).setup().get()
        exportDir = File(context.cacheDir, "dari_export")
        exportDir.deleteRecursively()
    }

    private fun entry(handler: String = "getUser", requestData: String = """{"id":1}""") = MessageEntry(
        requestId = "req-$handler",
        handlerName = handler,
        direction = MessageDirection.WEB_TO_APP,
        requestData = requestData,
        responseData = """{"ok":true}""",
        status = MessageStatus.SUCCESS,
        requestTimestamp = 1_000L,
        responseTimestamp = 1_200L,
    )

    // Reference output produced through the SAF path, used to compare against shared files.
    private fun saved(entries: List<MessageEntry>, format: ExportFormat): String = runBlocking {
        val file = File.createTempFile("saved", ".out", context.cacheDir)
        assertTrue(DariExporter.saveToUri(context, Uri.fromFile(file), entries, format))
        file.readText()
    }

    @Test
    fun `saveToUri writes JSON content to the output stream`() {
        val content = saved(listOf(entry()), ExportFormat.JSON)

        assertTrue(content.trimStart().startsWith("["))
        assertTrue(content.contains("\"handler_name\": \"getUser\""))
    }

    @Test
    fun `saveToUri writes TEXT content to the output stream`() {
        val content = saved(listOf(entry()), ExportFormat.TEXT)

        assertTrue(content.startsWith("Handler: getUser"))
        assertTrue(content.contains("Duration: 200 ms"))
    }

    @Test
    fun `saveToUri writes UTF-8 bytes for non-ASCII payloads`() = runBlocking {
        val file = File.createTempFile("utf8", ".txt", context.cacheDir)
        DariExporter.saveToUri(context, Uri.fromFile(file), listOf(entry(requestData = "\"안녕 👋\"")), ExportFormat.TEXT)

        val decoded = String(file.readBytes(), Charsets.UTF_8)
        assertTrue(decoded.contains("안녕 👋"))
    }

    @Test
    fun `saveToUri returns false when the uri cannot be opened`() = runBlocking {
        val missing = Uri.fromFile(File(context.cacheDir, "missing-dir/out.json"))

        assertFalse(DariExporter.saveToUri(context, missing, listOf(entry()), ExportFormat.JSON))
    }

    @Test
    fun `exportAndShare creates a file in the export cache dir with the format extension`() = runBlocking {
        DariExporter.exportAndShare(context, listOf(entry()), ExportFormat.JSON)
        DariExporter.exportAndShare(context, listOf(entry()), ExportFormat.TEXT)

        val names = exportDir.listFiles().orEmpty().map { it.name }
        assertEquals(1, names.count { it.startsWith("dari_export_") && it.endsWith(".json") })
        assertEquals(1, names.count { it.startsWith("dari_export_") && it.endsWith(".txt") })
    }

    @Test
    fun `exportAndShare file content matches the saved export`() = runBlocking {
        val entries = listOf(entry("a"), entry("b"))
        DariExporter.exportAndShare(context, entries, ExportFormat.JSON)

        assertEquals(saved(entries, ExportFormat.JSON), exportDir.listFiles()!!.single().readText())
    }

    @Test
    fun `exportAndShare keeps previous export files`() = runBlocking {
        repeat(3) { DariExporter.exportAndShare(context, listOf(entry()), ExportFormat.JSON) }

        assertEquals(3, exportDir.listFiles()!!.size)
    }

    @Test
    fun `exportAndShare launches a chooser with a readable FileProvider stream`() = runBlocking {
        DariExporter.exportAndShare(context, listOf(entry()), ExportFormat.JSON)

        val chooser = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("application/json", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(uri)
        assertEquals("${context.packageName}.dari.fileprovider", uri!!.authority)
    }

    @Test
    fun `exportAndShareSingle exports a single-element list`() = runBlocking {
        val single = entry("only")
        DariExporter.exportAndShareSingle(context, single, ExportFormat.JSON)

        assertEquals(saved(listOf(single), ExportFormat.JSON), exportDir.listFiles()!!.single().readText())
    }

    // FileProvider caches path roots statically, but Robolectric gives each test a new data dir.
    private fun clearFileProviderCache() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }
}
