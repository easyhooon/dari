package com.easyhooon.dari.gradle

import com.android.build.api.instrumentation.InstrumentationScope
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.provider.Property

/**
 * `dari { }`: where the app's bridge receives requests and sends responses.
 * Each mapping makes the plugin call the matching `DariInterceptor` method at the entry of that method.
 */
abstract class DariExtension {

    /** [InstrumentationScope.ALL] also rewrites third-party AARs, which re-dexes every one of them. */
    abstract val scope: Property<InstrumentationScope>

    /** Tag for every captured entry. Defaults to the name passed to `addJavascriptInterface`. */
    abstract val tag: Property<String>

    internal val mappings = mutableListOf<Pending>()

    internal class Pending(val className: String, val methodName: String, val spec: BridgeMappingSpec)

    fun webToAppRequest(className: String, methodName: String, action: Action<BridgeMappingSpec>) =
        map(MappingKind.WEB_TO_APP_REQUEST, className, methodName, action)

    fun webToAppResponse(className: String, methodName: String, action: Action<BridgeMappingSpec>) =
        map(MappingKind.WEB_TO_APP_RESPONSE, className, methodName, action)

    fun appToWebRequest(className: String, methodName: String, action: Action<BridgeMappingSpec>) =
        map(MappingKind.APP_TO_WEB_REQUEST, className, methodName, action)

    fun appToWebResponse(className: String, methodName: String, action: Action<BridgeMappingSpec>) =
        map(MappingKind.APP_TO_WEB_RESPONSE, className, methodName, action)

    private fun map(kind: MappingKind, className: String, methodName: String, action: Action<BridgeMappingSpec>) {
        val spec = BridgeMappingSpec(kind).also(action::execute)
        val where = "dari { ${kind.dslName}(\"$className\", \"$methodName\") }"
        if (kind.isRequest && spec.handlerName == null) throw GradleException("$where needs handlerName")
        if (!kind.isRequest && spec.requestId == null) throw GradleException("$where needs requestId")
        mappings += Pending(className, methodName, spec)
    }
}

internal enum class MappingKind(val dslName: String, val isRequest: Boolean) {
    WEB_TO_APP_REQUEST("webToAppRequest", true),
    WEB_TO_APP_RESPONSE("webToAppResponse", false),
    APP_TO_WEB_REQUEST("appToWebRequest", true),
    APP_TO_WEB_RESPONSE("appToWebResponse", false),
}

/** One mapping's fields. Each one is an expression over the mapped method's arguments. */
class BridgeMappingSpec internal constructor(internal val kind: MappingKind) {
    var handlerName: DariExpr? = null
    var displayName: DariExpr? = null
    var requestId: DariExpr? = null
    var data: DariExpr? = null

    /** Responses only. Unmapped means SUCCESS. */
    var success: DariExpr? = null

    /** Requests only. True resolves the entry at once; false defers to `DariConfig.fireAndForget`. */
    var fireAndForget: DariExpr? = null

    /** Optional; unmapped records every call. When set, records only when true, e.g. `json(arg(0), "type").equalsTo("request")`. */
    var onlyIf: DariExpr? = null

    /** Overrides `dari { tag }` for this mapping. */
    var tag: String? = null

    /** The [index]th parameter of the mapped method, starting at 0. */
    fun arg(index: Int): DariExpr = DariExpr("""{"arg":$index}""")

    fun constant(value: String): DariExpr = DariExpr("""{"const":${quote(value)}}""")

    fun constant(value: Boolean): DariExpr = DariExpr("""{"const":$value}""")

    /** A field of a JSON string or object, e.g. `json(arg(0), "meta.requestId")`. Array items take numeric keys. */
    fun json(source: DariExpr, path: String): DariExpr = DariExpr("""{"json":[${source.json},${quote(path)}]}""")

    /** Base64 text decoded to bytes; byte data is captured through the protobuf API. */
    fun base64(source: DariExpr): DariExpr = DariExpr("""{"base64":${source.json}}""")

    fun isNull(source: DariExpr): DariExpr = DariExpr("""{"isNull":${source.json}}""")

    internal fun toSpec(defaultTag: String?): String {
        val fields = listOf(
            "handlerName" to handlerName,
            "displayName" to displayName,
            "requestId" to requestId,
            "data" to data,
            "success" to success,
            "fireAndForget" to fireAndForget,
            "onlyIf" to onlyIf,
        ).mapNotNull { (name, expr) -> expr?.let { "${quote(name)}:${it.json}" } }
        val resolvedTag = (tag ?: defaultTag)?.let { "\"tag\":${quote(it)}," }.orEmpty()
        return """{"kind":"${kind.name}",$resolvedTag"fields":{${fields.joinToString(",")}}}"""
    }
}

/** A value read from the mapped method's arguments when it runs on the device. */
class DariExpr internal constructor(internal val json: String) {
    /** True when the value's text equals [value], otherwise false. */
    fun equalsTo(value: String): DariExpr = DariExpr("""{"equalsTo":[$json,${quote(value)}]}""")
}

private fun quote(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char < ' ' -> append("\\u%04x".format(char.code))
            else -> append(char)
        }
    }
    append('"')
}
