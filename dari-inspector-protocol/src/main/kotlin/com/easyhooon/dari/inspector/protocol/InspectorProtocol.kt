package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.json.Json

object InspectorProtocol {
    /** Exact-match protocol version; bump on any breaking wire change. */
    const val VERSION = 1

    /** Abstract socket name is `dari_<applicationId>`, reached via `adb forward tcp:0 localabstract:<name>`. */
    const val SOCKET_NAME_PREFIX = "dari_"

    /** Payloads are already capped by `DariConfig.maxContentLength`, so frames stay well below this. */
    const val MAX_FRAME_BYTES = 4 * 1024 * 1024

    fun socketName(applicationId: String): String = SOCKET_NAME_PREFIX + applicationId

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "type"
    }
}
