package com.easyhooon.dari.export

import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.MessagePayloadMetadata
import com.easyhooon.dari.PayloadContentType
import com.easyhooon.dari.PayloadDecodeStatus
import com.easyhooon.dari.RawPayloadPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportableMessageTest {

    private fun createEntry(
        id: Long = 1L,
        requestId: String? = "req-1",
        handlerName: String = "getAppInfo",
        direction: MessageDirection = MessageDirection.WEB_TO_APP,
        tag: String? = null,
        requestData: String? = """{"key":"value"}""",
        responseData: String? = """{"result":"ok"}""",
        requestDataTruncated: Boolean = false,
        responseDataTruncated: Boolean = false,
        status: MessageStatus = MessageStatus.SUCCESS,
        requestTimestamp: Long = 1000L,
        responseTimestamp: Long? = 1500L,
    ) = MessageEntry(
        id = id,
        requestId = requestId,
        handlerName = handlerName,
        direction = direction,
        tag = tag,
        requestData = requestData,
        responseData = responseData,
        requestDataTruncated = requestDataTruncated,
        responseDataTruncated = responseDataTruncated,
        status = status,
        requestTimestamp = requestTimestamp,
        responseTimestamp = responseTimestamp,
    )

    @Test
    fun `toExportable maps all fields correctly for WEB_TO_APP`() {
        val entry = createEntry()
        val exportable = entry.toExportable()

        assertEquals(1L, exportable.id)
        assertEquals("req-1", exportable.requestId)
        assertEquals("getAppInfo", exportable.handlerName)
        assertEquals("WEB_TO_APP", exportable.direction)
        assertNull(exportable.tag)
        assertEquals("""{"key":"value"}""", exportable.requestData)
        assertEquals("""{"result":"ok"}""", exportable.responseData)
        assertEquals(false, exportable.requestDataTruncated)
        assertEquals(false, exportable.responseDataTruncated)
        assertEquals("SUCCESS", exportable.status)
        assertEquals(1000L, exportable.requestTimestamp)
        assertEquals(1500L, exportable.responseTimestamp)
        assertEquals(500L, exportable.durationMs)
    }

    @Test
    fun `toExportable maps APP_TO_WEB direction`() {
        val entry = createEntry(direction = MessageDirection.APP_TO_WEB)
        val exportable = entry.toExportable()

        assertEquals("APP_TO_WEB", exportable.direction)
    }

    @Test
    fun `toExportable maps all status values`() {
        assertEquals("IN_PROGRESS", createEntry(status = MessageStatus.IN_PROGRESS).toExportable().status)
        assertEquals("SUCCESS", createEntry(status = MessageStatus.SUCCESS).toExportable().status)
        assertEquals("ERROR", createEntry(status = MessageStatus.ERROR).toExportable().status)
    }

    @Test
    fun `toExportable preserves tag`() {
        val entry = createEntry(tag = "PaymentWebView")
        assertEquals("PaymentWebView", entry.toExportable().tag)
    }

    @Test
    fun `toExportable handles null optional fields`() {
        val entry = createEntry(
            requestId = null,
            tag = null,
            requestData = null,
            responseData = null,
            responseTimestamp = null,
        )
        val exportable = entry.toExportable()

        assertNull(exportable.requestId)
        assertNull(exportable.tag)
        assertNull(exportable.requestData)
        assertNull(exportable.responseData)
        assertNull(exportable.responseTimestamp)
        assertNull(exportable.durationMs)
    }

    @Test
    fun `toExportable preserves truncation flags`() {
        val entry = createEntry(
            requestDataTruncated = true,
            responseDataTruncated = true,
        )
        val exportable = entry.toExportable()

        assertEquals(true, exportable.requestDataTruncated)
        assertEquals(true, exportable.responseDataTruncated)
    }

    @Test
    fun `toExportable includes protobuf payload metadata`() {
        val metadata = MessagePayloadMetadata(
            contentType = PayloadContentType.PROTOBUF,
            originalSizeBytes = 7,
            decodeStatus = PayloadDecodeStatus.DECODED,
            rawPreview = RawPayloadPreview("AQID", 3, true),
        )
        val exportable = createEntry().copy(
            requestPayloadMetadata = metadata,
            responsePayloadMetadata = metadata.copy(decodeStatus = PayloadDecodeStatus.FAILED),
        ).toExportable()

        assertEquals("PROTOBUF", exportable.requestContentType)
        assertEquals(7, exportable.requestOriginalSizeBytes)
        assertEquals("DECODED", exportable.requestDecodeStatus)
        assertEquals("AQID", exportable.requestRawPreviewBase64)
        assertEquals(3, exportable.requestRawPreviewSizeBytes)
        assertEquals(true, exportable.requestRawPreviewTruncated)
        assertEquals("PROTOBUF", exportable.responseContentType)
        assertEquals(7, exportable.responseOriginalSizeBytes)
        assertEquals("FAILED", exportable.responseDecodeStatus)
        assertEquals("AQID", exportable.responseRawPreviewBase64)
        assertEquals(3, exportable.responseRawPreviewSizeBytes)
        assertEquals(true, exportable.responseRawPreviewTruncated)
    }
}
