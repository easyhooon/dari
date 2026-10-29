package com.easyhooon.dari.desktop.app.ios

import com.easyhooon.dari.desktop.app.adb.AdbDevice
import com.easyhooon.dari.desktop.app.client.InspectorConnection
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** iOS targets for the device picker: this Mac's Simulators as one entry, plus iPhones and iPads attached over USB. */
class IosDevices(
    private val clientVersion: String,
    private val usbmux: Usbmux? = Usbmux().takeIf { it.isAvailable },
    private val simulator: IosAppFinder = IosAppFinder(clientVersion, connect = IosAppFinder::loopbackSocket),
) {
    // Serial → usbmuxd's current id. Replugging changes the id, so it's refreshed by every usbDevices() call.
    @Volatile
    private var attached: Map<String, UsbmuxDevice> = emptyMap()
    private val names = ConcurrentHashMap<String, String>()

    // Serial → when its name last failed to load, so a nameless device isn't asked again on every poll.
    private val nameFailures = ConcurrentHashMap<String, Long>()

    /** Blocking: devices attached over USB right now. A failed query keeps the last list, so devices don't flicker. */
    fun usbDevices(): List<AdbDevice> {
        val mux = usbmux ?: return emptyList()
        val found = runCatching { mux.devices() }.getOrElse { return attached.values.map(::toAdbDevice) }
        attached = found.associateBy { USB_PREFIX + it.serial }
        found.forEach { device -> loadName(mux, device) }
        return found.map(::toAdbDevice)
    }

    private fun loadName(mux: Usbmux, device: UsbmuxDevice) {
        val serial = USB_PREFIX + device.serial
        if (names.containsKey(serial)) return
        val failedAt = nameFailures[serial]
        if (failedAt != null && System.currentTimeMillis() - failedAt < NAME_RETRY_MS) return
        mux.deviceName(device.deviceId)?.let { names[serial] = it } ?: nameFailures.put(serial, System.currentTimeMillis())
    }

    private fun toAdbDevice(device: UsbmuxDevice): AdbDevice {
        val serial = USB_PREFIX + device.serial
        return AdbDevice(serial, "iOS device", names[serial])
    }

    /** Blocking: Dari apps answering on [serial], which must be an iOS target. */
    fun apps(serial: String): List<String> = finder(serial)?.apps().orEmpty()

    /** Blocking: connects to [applicationId] on [serial]. */
    fun open(serial: String, applicationId: String): InspectorConnection =
        finder(serial)?.open(applicationId) ?: throw IOException("$serial is not attached")

    private fun finder(serial: String): IosAppFinder? {
        if (serial == SIMULATOR_SERIAL) return simulator
        val device = attached[serial] ?: return null
        val mux = usbmux ?: return null
        return IosAppFinder(clientVersion) { port -> mux.connect(device.deviceId, port).asLoopbackSocket() }
    }

    companion object {
        /** Stands in for a device in the device picker; all Simulators on this Mac share it. */
        const val SIMULATOR_SERIAL = "ios-simulator"
        val SIMULATOR = AdbDevice(SIMULATOR_SERIAL, "iOS Simulator")
        private const val USB_PREFIX = "ios:"
        private const val NAME_RETRY_MS = 30_000L

        fun isIos(serial: String): Boolean = serial == SIMULATOR_SERIAL || serial.startsWith(USB_PREFIX)
    }
}
