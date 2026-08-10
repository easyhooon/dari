package com.easyhooon.dari

/** Metadata retained when a binary payload is rendered as text for inspection. */
data class MessagePayloadMetadata(
    val contentType: PayloadContentType,
    val originalSizeBytes: Int,
    val decodeStatus: PayloadDecodeStatus,
    val rawPreview: RawPayloadPreview? = null,
)

/** Bounded binary preview retained for raw payload inspection. */
data class RawPayloadPreview(
    val base64: String,
    val previewSizeBytes: Int,
    val truncated: Boolean,
)

enum class PayloadContentType {
    PROTOBUF,
}

/** Result of converting a binary payload into inspectable display text. */
enum class PayloadDecodeStatus {
    DECODED,
    DECODER_UNAVAILABLE,
    FAILED,
}
