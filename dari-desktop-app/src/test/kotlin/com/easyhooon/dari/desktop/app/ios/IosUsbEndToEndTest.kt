package com.easyhooon.dari.desktop.app.ios

import com.easyhooon.dari.inspector.protocol.ObserveEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reaches a Dari app on an iPhone attached over USB through the real usbmuxd.
 * Skipped unless run with `-Pdari.iosUsbApp=<bundle id>` while that app is open on the device.
 */
class IosUsbEndToEndTest {
    @Test
    fun `desktop reaches a Dari app on a USB iPhone`() = runBlocking {
        val applicationId = System.getProperty("dari.iosUsbApp")
        assumeTrue("set -Pdari.iosUsbApp to run", !applicationId.isNullOrBlank())
        val ios = IosDevices("test")
        val device = ios.usbDevices().firstOrNull()
        assumeTrue("no iPhone attached over USB", device != null)
        println("USB device: ${device!!.serial} name=${device.name}")

        assertTrue(applicationId in ios.apps(device.serial), "apps on ${device.serial}: ${ios.apps(device.serial)}")
        ios.open(device.serial, applicationId).use { conn ->
            assertEquals("ios", conn.app.platform)
            val events = conn.observe()
            println("messages before clear: ${conn.list().map { "${it.handlerName}:${it.status}" }}")
            conn.clear()
            withTimeout(5_000) { assertEquals(ObserveEvent.Cleared, events.receive()) }
            assertEquals(emptyList(), conn.list())
        }
    }
}
