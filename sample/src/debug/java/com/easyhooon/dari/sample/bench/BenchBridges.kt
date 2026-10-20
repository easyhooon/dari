package com.easyhooon.dari.sample.bench

import com.easyhooon.dari.interceptor.DariInterceptor
import org.json.JSONObject

// Debug-only bridges for BridgeOverheadBenchmark. Each trio has the same body;
// only how the call is captured differs: not at all, by hand, or by a `dari { }` mapping.

interface PositionalBridge {
    fun onRequest(handlerName: String, requestId: String, data: String?)
    fun respond(handlerName: String, requestId: String, success: Boolean, data: String?)
}

interface JsonMessageBridge {
    fun onMessage(message: String)
    fun reply(message: String)
}

/** Keeps the bodies from being optimized away. */
object Sink {
    @Volatile
    var last: Int = 0
}

class BarePositionalBridge : PositionalBridge {
    override fun onRequest(handlerName: String, requestId: String, data: String?) {
        Sink.last = data?.length ?: 0
    }

    override fun respond(handlerName: String, requestId: String, success: Boolean, data: String?) {
        Sink.last = data?.length ?: 0
    }
}

class MappedPositionalBridge : PositionalBridge {
    override fun onRequest(handlerName: String, requestId: String, data: String?) {
        Sink.last = data?.length ?: 0
    }

    override fun respond(handlerName: String, requestId: String, success: Boolean, data: String?) {
        Sink.last = data?.length ?: 0
    }
}

class ManualPositionalBridge(private val interceptor: DariInterceptor?) : PositionalBridge {
    override fun onRequest(handlerName: String, requestId: String, data: String?) {
        interceptor?.onWebToAppRequest(handlerName, requestId, data)
        Sink.last = data?.length ?: 0
    }

    override fun respond(handlerName: String, requestId: String, success: Boolean, data: String?) {
        interceptor?.onWebToAppResponse(handlerName, requestId, data, success)
        Sink.last = data?.length ?: 0
    }
}

// JSON bridges parse the message to dispatch, as a real one-string bridge must.
class BareJsonBridge : JsonMessageBridge {
    override fun onMessage(message: String) {
        Sink.last = JSONObject(message).optString("handler").length
    }

    override fun reply(message: String) {
        Sink.last = JSONObject(message).optString("handler").length
    }
}

class MappedJsonBridge : JsonMessageBridge {
    override fun onMessage(message: String) {
        Sink.last = JSONObject(message).optString("handler").length
    }

    override fun reply(message: String) {
        Sink.last = JSONObject(message).optString("handler").length
    }
}

// Reuses the parsed message, so the manual case pays for one parse like the bare one.
class ManualJsonBridge(private val interceptor: DariInterceptor?) : JsonMessageBridge {
    override fun onMessage(message: String) {
        val json = JSONObject(message)
        interceptor?.onWebToAppRequest(json.getString("handler"), json.getString("id"), json.opt("data")?.toString())
        Sink.last = json.optString("handler").length
    }

    override fun reply(message: String) {
        val json = JSONObject(message)
        interceptor?.onWebToAppResponse(
            json.getString("handler"),
            json.getString("id"),
            json.opt("data")?.toString(),
            json.getBoolean("ok"),
        )
        Sink.last = json.optString("handler").length
    }
}
