package com.easyhooon.dari.kmp

import kotlin.test.Test
import kotlin.test.assertNull

class DariKmpAndroidTest {
    // With dari-noop on the classpath, as in a release build, the factory delegates and returns null.
    @Test
    fun delegatesToDariNoop() {
        assertNull(DariKmp.createInterceptor("test"))
    }
}
