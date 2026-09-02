package com.easyhooon.dari

import org.junit.Assert.assertNull
import org.junit.Test

class DariNoopApiTest {
    @Test
    fun `display name request API remains a no-op`() {
        val interceptor = Dari.createInterceptor()

        interceptor?.onWebToAppRequest(
            handlerName = "N:4-1",
            displayName = "오늘의 건강목표",
            requestId = "request-id",
            requestData = "payload",
        )

        assertNull(interceptor)
    }
}
