package com.easyhooon.dari.inspector

import com.easyhooon.dari.MessageEntry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Default masking applied to every message before it leaves the device.
 * Fails closed: if a payload can't be processed, it is replaced instead of sent raw.
 */
internal object DesktopPayloadMasker {
    const val MASK = "***"
    const val REDACTION_FAILED = "[dari: payload hidden, masking failed]"

    // Matched against keys lowercased with separators removed, e.g. "Access-Token" -> "accesstoken".
    private val SENSITIVE_KEY_PARTS = listOf(
        "token", "password", "passwd", "secret", "authorization", "cookie", "apikey", "sessionid", "cardnumber",
    )
    private val SENSITIVE_KEYS = setOf("cvv", "cvc", "ssn", "otp", "pin")

    // `key: value` / `key=value` in decoded protobuf text, query strings and other non-JSON text.
    private val KEY_VALUE = Regex(
        """(?i)\b([\w.\-]*(?:token|password|passwd|secret|authorization|cookie|api[_\-]?key|session[_\-]?id|card[_\-]?number)[\w.\-]*)(["']?\s*[:=]\s*)("(?:[^"\\]|\\.)*"|'[^']*'|[^\s,;&}\]]+)""",
    )
    private val AUTH_SCHEME = Regex("""(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/\-]+=*""")

    private val json = Json { isLenient = false }

    /** Raw previews are dropped: they carry undecoded bytes that key-based masking can't inspect. */
    fun mask(entry: MessageEntry): MessageEntry = entry.copy(
        requestData = maskText(entry.requestData),
        responseData = maskText(entry.responseData),
        requestPayloadMetadata = entry.requestPayloadMetadata?.copy(rawPreview = null),
        responsePayloadMetadata = entry.responsePayloadMetadata?.copy(rawPreview = null),
    )

    @Suppress("TooGenericExceptionCaught") // Any failure must hide the payload rather than leak it.
    fun maskText(text: String?): String? {
        if (text == null) return null
        return try {
            val masked = maskJsonOrNull(text) ?: maskKeyValues(text)
            AUTH_SCHEME.replace(masked) { "${it.groupValues[1]} $MASK" }
        } catch (_: Exception) {
            REDACTION_FAILED
        } catch (_: StackOverflowError) {
            REDACTION_FAILED
        }
    }

    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().filter { it.isLetterOrDigit() }
        return normalized in SENSITIVE_KEYS || SENSITIVE_KEY_PARTS.any { it in normalized }
    }

    private fun maskJsonOrNull(text: String): String? {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        val element = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return null
        }
        return maskElement(element).toString()
    }

    private fun maskElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.mapValues { (key, value) ->
                if (isSensitiveKey(key)) JsonPrimitive(MASK) else maskElement(value)
            },
        )
        is JsonArray -> JsonArray(element.map(::maskElement))
        // Bridges often nest stringified JSON; mask inside it too.
        is JsonPrimitive -> if (element.isString) JsonPrimitive(maskText(element.content)) else element
    }

    private fun maskKeyValues(text: String): String = KEY_VALUE.replace(text) { match ->
        val value = match.groupValues[3]
        val maskedValue = when {
            value.startsWith("\"") -> "\"$MASK\""
            value.startsWith("'") -> "'$MASK'"
            else -> MASK
        }
        match.groupValues[1] + match.groupValues[2] + maskedValue
    }
}
