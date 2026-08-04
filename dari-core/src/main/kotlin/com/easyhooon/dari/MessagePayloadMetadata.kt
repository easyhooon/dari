package com.easyhooon.dari

/** Metadata retained when a binary payload is rendered as text for inspection. */
data class MessagePayloadMetadata(
    val contentType: PayloadContentType,
    val originalSizeBytes: Int,
    val decodeStatus: PayloadDecodeStatus,
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
