package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Swift SDK in dari-ios reimplements this protocol; both sides test against these fixtures.
 * Regenerate after an intended wire change with `-Pdari.updateIosFixtures`.
 */
class IosWireFixturesTest {
    private val dir = File(requireNotNull(System.getProperty("dari.iosFixtures")))
    private val update = System.getProperty("dari.updateIosFixtures").toBoolean()

    private val summary = WireMessageSummary(
        id = 3,
        requestId = "1",
        handlerName = "getUser",
        displayName = "Get user",
        direction = "WEB_TO_APP",
        tag = "demo",
        status = "SUCCESS",
        requestTimestamp = 1_700_000_000_000,
        responseTimestamp = 1_700_000_000_250,
        requestSizeBytes = 8,
        responseSizeBytes = 15,
        pageUrl = "https://example.com/checkout",
    )

    @Test
    fun `desktop to app frames`() {
        check("client_hello", ClientHello.serializer(), ClientHello(InspectorProtocol.VERSION, "dari-desktop", "3.0.0"))
        check(
            "invoke_detail",
            InspectorFrame.serializer(),
            InspectorFrame.Invoke("7", InspectorOperations.DETAIL, buildJsonObject { put("id", 3) }),
        )
        check("invoke_observe", InspectorFrame.serializer(), InspectorFrame.Invoke("8", InspectorOperations.OBSERVE))
        check("cancel", InspectorFrame.serializer(), InspectorFrame.Cancel("8"))
    }

    @Test
    fun `app to desktop frames`() {
        check(
            "server_hello",
            ServerHello.serializer(),
            ServerHello(
                accepted = true,
                protocolVersion = InspectorProtocol.VERSION,
                app = AppInfo(
                    applicationId = "com.example.app",
                    appName = "Example",
                    appVersion = "1.0",
                    dariVersion = "3.0.0-alpha01",
                    deviceModel = "iPhone17,1 (Simulator)",
                    sdkInt = 0,
                    environment = mapOf("server" to "demo"),
                    platform = "ios",
                    osVersion = "18.2.0",
                ),
            ),
        )
        check("result_list", InspectorFrame.serializer(), result("1", ListOutput.serializer(), ListOutput(listOf(summary))))
        check(
            "result_detail",
            InspectorFrame.serializer(),
            result(
                "2",
                WireMessageDetail.serializer(),
                WireMessageDetail(summary, requestData = """{"id":7}""", responseData = """{"name":"Dari"}"""),
            ),
        )
        check(
            "result_observe_added",
            InspectorFrame.serializer(),
            result("3", ObserveEvent.serializer(), ObserveEvent.Added(summary), isFinal = false),
        )
        check(
            "result_observe_cleared",
            InspectorFrame.serializer(),
            result("3", ObserveEvent.serializer(), ObserveEvent.Cleared, isFinal = false),
        )
        check("result_clear", InspectorFrame.serializer(), InspectorFrame.Result("4", output = JsonObject(emptyMap())))
        check(
            "result_error",
            InspectorFrame.serializer(),
            InspectorFrame.Result("5", error = InspectorError(InspectorErrorCode.OPERATION_NOT_FOUND, "Unknown op x")),
        )
        check("result_search", InspectorFrame.serializer(), result("6", SearchOutput.serializer(), SearchOutput(listOf(3))))
    }

    private fun <T> result(requestId: String, serializer: KSerializer<T>, value: T, isFinal: Boolean = true) =
        InspectorFrame.Result(requestId, output = InspectorProtocol.json.encodeToJsonElement(serializer, value), isFinal = isFinal)

    private fun <T> check(name: String, serializer: KSerializer<T>, value: T) {
        val file = File(dir, "$name.json")
        val encoded = InspectorProtocol.json.encodeToJsonElement(serializer, value)
        if (update) {
            file.writeText(prettyJson.encodeToString(JsonElement.serializer(), encoded) + "\n")
            return
        }
        assertEquals(InspectorProtocol.json.parseToJsonElement(file.readText()), encoded, "$name.json is stale")
        assertEquals(value, InspectorProtocol.json.decodeFromString(serializer, file.readText()))
    }

    private companion object {
        val prettyJson = Json { prettyPrint = true }
    }
}
