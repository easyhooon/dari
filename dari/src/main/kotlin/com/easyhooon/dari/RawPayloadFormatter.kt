package com.easyhooon.dari

import java.util.Base64

internal object RawPayloadFormatter {
    fun formatHex(preview: RawPayloadPreview): String {
        val bytes = runCatching {
            Base64.getDecoder().decode(preview.base64)
        }.getOrElse {
            return "(raw preview unavailable)"
        }
        if (bytes.isEmpty()) return "(empty)"

        return bytes.asIterable()
            .chunked(16)
            .joinToString("\n") { line ->
                line.joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            }
    }
}
