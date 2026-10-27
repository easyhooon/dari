package com.easyhooon.dari.gradle

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeMappingSpecTest {

    private val extension = object : DariExtension() {
        override val scope get() = throw UnsupportedOperationException()
        override val tag get() = throw UnsupportedOperationException()
        override val failOnUnmatchedMapping get() = throw UnsupportedOperationException()
        override val nonDebuggableBuildTypes get() = throw UnsupportedOperationException()
    }

    @Test
    fun `positional mapping serializes to the format DariHooks parses`() {
        extension.webToAppRequest(
            "a.B",
            "onRequest",
            mapping {
                handlerName = arg(0)
                requestId = arg(1)
                data = arg(2)
            },
        )

        // Same literal as REQUEST in :dari's DariHooksTest.
        assertEquals(
            """{"kind":"WEB_TO_APP_REQUEST","fields":{"handlerName":{"arg":0},"requestId":{"arg":1},"data":{"arg":2}}}""",
            extension.mappings.single().spec.toSpec(defaultTag = null),
        )
    }

    @Test
    fun `nested expressions, constants and tags serialize and escape`() {
        extension.webToAppResponse(
            "a.B",
            "send",
            mapping {
                handlerName = json(arg(0), "meta.name")
                requestId = arg(1)
                success = isNull(json(arg(0), "error"))
                data = base64(arg(2))
                displayName = constant("Say \"hi\"")
                tag = "Own"
            },
        )

        assertEquals(
            """{"kind":"WEB_TO_APP_RESPONSE","tag":"Own","fields":{"handlerName":{"json":[{"arg":0},"meta.name"]},""" +
                """"displayName":{"const":"Say \"hi\""},"requestId":{"arg":1},"data":{"base64":{"arg":2}},""" +
                """"success":{"isNull":{"json":[{"arg":0},"error"]}}}}""",
            extension.mappings.single().spec.toSpec(defaultTag = "Default"),
        )
    }

    @Test
    fun `equalsTo and the default tag`() {
        extension.webToAppRequest(
            "a.B",
            "m",
            mapping {
                handlerName = arg(0)
                fireAndForget = arg(0).equalsTo("ping")
            },
        )

        assertEquals(
            """{"kind":"WEB_TO_APP_REQUEST","tag":"Sample","fields":{"handlerName":{"arg":0},""" +
                """"fireAndForget":{"equalsTo":[{"arg":0},"ping"]}}}""",
            extension.mappings.single().spec.toSpec(defaultTag = "Sample"),
        )
    }

    @Test
    fun `onlyIf serializes as a field`() {
        extension.appToWebRequest(
            "a.B",
            "postMessage",
            mapping {
                handlerName = json(arg(0), "action")
                onlyIf = json(arg(0), "type").equalsTo("request")
            },
        )

        assertEquals(
            """{"kind":"APP_TO_WEB_REQUEST","fields":{"handlerName":{"json":[{"arg":0},"action"]},""" +
                """"onlyIf":{"equalsTo":[{"json":[{"arg":0},"type"]},"request"]}}}""",
            extension.mappings.single().spec.toSpec(defaultTag = null),
        )
    }

    @Test(expected = GradleException::class)
    fun `a request without handlerName fails the build`() {
        extension.webToAppRequest("a.B", "m", mapping { requestId = arg(0) })
    }

    @Test(expected = GradleException::class)
    fun `a response without requestId fails the build`() {
        extension.appToWebResponse("a.B", "m", mapping { data = arg(0) })
    }

    // Build scripts get the spec as the lambda receiver; plain Kotlin needs this adapter.
    private fun mapping(block: BridgeMappingSpec.() -> Unit) = Action<BridgeMappingSpec> { it.block() }
}
