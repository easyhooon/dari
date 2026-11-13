package com.easyhooon.dari.kmp

import kotlin.test.Test
import kotlin.test.assertNull

class DariKmpIosTest {
    // The test binary doesn't link the Swift SDK, so the lookup must fail without crashing.
    @Test
    fun returnsNullWithoutSwiftSdk() {
        assertNull(DariKmp.createInterceptor("test"))
    }
}
