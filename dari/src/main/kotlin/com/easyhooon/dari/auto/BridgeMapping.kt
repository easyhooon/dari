package com.easyhooon.dari.auto

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** One `dari { }` mapping, decoded from the spec string the Gradle plugin bakes into the mapped method. */
internal class BridgeMapping(
    val kind: Kind,
    val tag: String?,
    private val fields: Map<String, Expr>,
) {
    enum class Kind { WEB_TO_APP_REQUEST, WEB_TO_APP_RESPONSE, APP_TO_WEB_REQUEST, APP_TO_WEB_RESPONSE }

    fun has(field: String): Boolean = field in fields

    /** Evaluates every mapped field once against the method's arguments. */
    fun evaluate(args: Array<Any?>): Map<String, Any?> {
        val parsed = HashMap<String, Any?>()
        return fields.mapValues { (_, expr) -> expr.evaluate(args, parsed) }
    }

    sealed interface Expr {
        // Parsed JSON is cached per call so several `json(arg(0), …)` fields parse the argument once.
        fun evaluate(args: Array<Any?>, parsed: MutableMap<String, Any?>): Any?
    }

    private class Arg(val index: Int) : Expr {
        override fun evaluate(args: Array<Any?>, parsed: MutableMap<String, Any?>): Any? = args.getOrNull(index)
    }

    private class Constant(val value: Any?) : Expr {
        override fun evaluate(args: Array<Any?>, parsed: MutableMap<String, Any?>): Any? = value
    }

    private class JsonPath(val source: Expr, val path: List<String>) : Expr {
        override fun evaluate(args: Array<Any?>, parsed: MutableMap<String, Any?>): Any? {
            var current: Any? = when (val value = source.evaluate(args, parsed)) {
                is String -> parsed.getOrPut(value) { parseJson(value) }
                is JSONObject, is JSONArray, null -> value
                // Other JSON types (kotlinx.serialization, Gson) print themselves as JSON.
                else -> value.toString().let { text -> parsed.getOrPut(text) { parseJson(text) } }
            }
            for (key in path) {
                current = when (current) {
                    is JSONObject -> current.opt(key)
                    is JSONArray -> key.toIntOrNull()?.let(current::opt)
                    else -> null
                }
            }
            return current.takeUnless { it == JSONObject.NULL }
        }
    }

    private class Base64Bytes(val source: Expr) : Expr {
        override fun evaluate(args: Array<Any?>, parsed: MutableMap<String, Any?>): Any? =
            (source.evaluate(args, parsed) as? String)?.let { Base64.decode(it, Base64.DEFAULT) }
    }

    private class EqualsTo(val source: Expr, val expected: String) : Expr {
        override fun evaluate(args: Array<Any?>, parsed: MutableMap<String, Any?>): Boolean =
            source.evaluate(args, parsed)?.toString() == expected
    }

    private class IsNull(val source: Expr) : Expr {
        override fun evaluate(args: Array<Any?>, parsed: MutableMap<String, Any?>): Boolean =
            source.evaluate(args, parsed) == null
    }

    companion object {
        fun parse(spec: String): BridgeMapping {
            val json = JSONObject(spec)
            val fields = json.getJSONObject("fields")
            return BridgeMapping(
                kind = Kind.valueOf(json.getString("kind")),
                tag = json.optString("tag").takeIf { it.isNotEmpty() },
                fields = fields.keys().asSequence().associateWith { parseExpr(fields.getJSONObject(it)) },
            )
        }

        private fun parseExpr(json: JSONObject): Expr = when {
            json.has("arg") -> Arg(json.getInt("arg"))
            json.has("const") -> Constant(json.get("const").takeUnless { it == JSONObject.NULL })
            json.has("json") -> json.getJSONArray("json").let { JsonPath(parseExpr(it.getJSONObject(0)), it.getString(1).split('.')) }
            json.has("base64") -> Base64Bytes(parseExpr(json.getJSONObject("base64")))
            json.has("equalsTo") -> json.getJSONArray("equalsTo").let { EqualsTo(parseExpr(it.getJSONObject(0)), it.getString(1)) }
            json.has("isNull") -> IsNull(parseExpr(json.getJSONObject("isNull")))
            else -> throw IllegalArgumentException("Unknown Dari mapping expression: $json")
        }

        // A string that isn't JSON yields null, so the field reads as absent instead of failing the whole entry.
        private fun parseJson(value: String): Any? = when (value.trimStart().firstOrNull()) {
            '{' -> runCatching { JSONObject(value) }.getOrNull()
            '[' -> runCatching { JSONArray(value) }.getOrNull()
            else -> null
        }
    }
}
