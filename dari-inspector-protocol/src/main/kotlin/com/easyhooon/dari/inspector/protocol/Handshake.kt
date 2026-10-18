package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.Serializable

/** First frame, sent by the desktop. adb only opens the app socket once the client writes. */
@Serializable
data class ClientHello(
    val protocolVersion: Int,
    val clientName: String,
    val clientVersion: String,
)

/** App reply to [ClientHello]. The session continues only when [accepted] is true. */
@Serializable
data class ServerHello(
    val accepted: Boolean,
    val protocolVersion: Int,
    val rejection: HandshakeRejection? = null,
    val app: AppInfo? = null,
)

@Serializable
enum class HandshakeRejection {
    UNSUPPORTED_PROTOCOL_VERSION,
}

@Serializable
data class AppInfo(
    val applicationId: String,
    val appName: String,
    val appVersion: String,
    val dariVersion: String,
    val deviceModel: String,
    val sdkInt: Int,
    /** Labels from `Dari.setEnvironment`; null from older apps or when none are set. */
    val environment: Map<String, String>? = null,
)
