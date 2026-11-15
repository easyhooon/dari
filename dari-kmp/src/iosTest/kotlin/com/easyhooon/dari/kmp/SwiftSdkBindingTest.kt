package com.easyhooon.dari.kmp

import com.easyhooon.dari.kmp.fakes.dari_kmp_fake_calls
import com.easyhooon.dari.kmp.fakes.dari_kmp_fake_tag
import com.easyhooon.dari.kmp.fakes.dari_kmp_fakes_register
import com.easyhooon.dari.kmp.objc.dari_kmp_interceptor_of
import com.easyhooon.dari.kmp.objc.dari_kmp_status_of
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Runs the cinterop path against ObjC fakes, so the Kotlin -> .def -> objc_msgSend side is checked without the Swift SDK.
// DariObjCTests in dari-ios checks the .def against the real Swift classes.
@OptIn(ExperimentalForeignApi::class)
class SwiftSdkBindingTest {
    @BeforeTest
    fun registerFakes() = dari_kmp_fakes_register()

    @Test
    fun statusIsReadyWhenEverySelectorExists() {
        assertEquals(0, dari_kmp_status_of(FACTORY, INTERCEPTOR))
    }

    @Test
    fun statusIsMissingWhenAClassIsAbsent() {
        assertEquals(1, dari_kmp_status_of("DariKmpNoSuchClass", INTERCEPTOR))
        assertEquals(1, dari_kmp_status_of(FACTORY, "DariKmpNoSuchClass"))
    }

    @Test
    fun statusIsOutdatedWhenASelectorIsMissing() {
        assertEquals(2, dari_kmp_status_of("DariKmpFakeOldFactory", INTERCEPTOR))
        assertEquals(2, dari_kmp_status_of(FACTORY, "DariKmpFakeOldInterceptor"))
    }

    @Test
    fun onlyReadyStatusEnablesTheSdk() {
        assertTrue(isSwiftSdkReady(0))
        assertFalse(isSwiftSdkReady(1))
        assertFalse(isSwiftSdkReady(2))
    }

    @Test
    fun factoryPassesTheTag() {
        val target = assertNotNull(dari_kmp_interceptor_of(FACTORY, "kmp"))
        assertEquals("kmp", dari_kmp_fake_tag(target))
    }

    @Test
    fun forwardsEveryCallWithItsArguments() {
        val target = assertNotNull(dari_kmp_interceptor_of(FACTORY, "kmp"))
        val interceptor = SwiftDariInterceptor(target, "kmp")

        interceptor.onPageUrlChanged("https://example.com")
        interceptor.onWebToAppRequest(handlerName = "getUser", requestId = "1", requestData = "{}", fireAndForget = null)
        interceptor.onWebToAppRequest("getUser", "Get user", "2", "{}", true)
        interceptor.onWebToAppResponse("getUser", "2", "ok", true)
        interceptor.onAppToWebRequest("log", null, "3", "hi", false)
        interceptor.onAppToWebResponse("9", false, "err")

        assertEquals(
            listOf(
                "page https://example.com",
                "w2aReq getUser (null) 1 {} (null)",
                "w2aReq getUser Get user 2 {} 1",
                "w2aRes getUser 2 ok 1",
                "a2wReq log (null) 3 hi 0",
                "a2wRes 9 0 err",
            ),
            dari_kmp_fake_calls(target),
        )
    }

    private companion object {
        const val FACTORY = "DariKmpFakeFactory"
        const val INTERCEPTOR = "DariKmpFakeInterceptor"
    }
}
