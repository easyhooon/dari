package com.easyhooon.dari.desktop.app.adb

import com.easyhooon.dari.desktop.app.ui.encodeQr
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WifiPairingTest {

    private val mdns = """
        List of discovered mdns services
        dari-svc00001	_adb-tls-pairing._tcp	192.168.0.12:40001
        adb-R5CX123-AbCdEf	_adb-tls-pairing._tcp	192.168.0.30:40002
        adb-R5CX999-XyZ	_adb-tls-connect._tcp	192.168.0.12:37000
    """.trimIndent()

    @Test
    fun `parses mdns services and skips the header`() {
        val services = Adb.parseMdnsServices(mdns)
        assertEquals(3, services.size)
        assertEquals(MdnsService("dari-svc00001", "_adb-tls-pairing._tcp", "192.168.0.12:40001"), services[0])
        assertTrue(services[2].isConnect)
        assertEquals("192.168.0.12", services[2].host)
    }

    @Test
    fun `builds the QR payload from alphanumeric secrets`() {
        val pairing = WifiPairing(Adb { "" }, { n -> "a".repeat(n) }, {})
        assertEquals("WIFI:T:ADB;S:dari-aaaaaaaa;P:aaaaaaaaaaaa;;", pairing.qrPayload)
    }

    @Test
    fun `tells our scanned QR apart from pairing-code requests`() {
        val pairing = WifiPairing(Adb { if (it == listOf("mdns", "services")) mdns else "" }, { n -> "svc00001".take(n).padEnd(n, '0') }, {})
        val scan = pairing.scan()
        assertEquals("192.168.0.12:40001", scan.qr?.address)
        assertEquals(listOf("adb-R5CX123-AbCdEf"), scan.codeRequests.map { it.name })
    }

    @Test
    fun `no QR match while the phone has not scanned`() {
        val pairing = WifiPairing(Adb { if (it == listOf("mdns", "services")) mdns else "" }, { n -> "z".repeat(n) }, {})
        assertNull(pairing.scan().qr)
    }

    @Test
    fun `reports the new serial after a successful pair`() = runBlocking {
        var polls = 0
        val adb = Adb { args ->
            when (args.first()) {
                "pair" -> "Successfully paired to 192.168.0.30:40002 [guid=adb-R5CX123]"
                "devices" -> if (polls++ == 0) {
                    "List of devices attached\nR5CXUSB\tdevice\n"
                } else {
                    "List of devices attached\nR5CXUSB\tdevice\nadb-R5CX123-AbCdEf._adb-tls-connect._tcp\tdevice\n"
                }
                else -> ""
            }
        }
        val result = WifiPairing(adb, { "x".repeat(it) }, {}).pairWithCode("192.168.0.30:40002", "123456")
        assertEquals(PairResult.Connected("adb-R5CX123-AbCdEf._adb-tls-connect._tcp"), result)
    }

    @Test
    fun `surfaces adb's message for a wrong code`() = runBlocking {
        val adb = Adb { args ->
            if (args.first() == "pair") throw IOException("Failed: Wrong password or connection was dropped.") else "List of devices attached\n"
        }
        val result = WifiPairing(adb, { "x".repeat(it) }, {}).pairWithCode("192.168.0.30:40002", "000000")
        assertEquals(PairResult.Failed("Failed: Wrong password or connection was dropped."), result)
    }

    @Test
    fun `a wrong code that exits 0 still fails`() = runBlocking {
        val adb = Adb { args -> if (args.first() == "pair") "Failed: Wrong password or connection was dropped.\n" else "List of devices attached\n" }
        val result = WifiPairing(adb, { "x".repeat(it) }, {}).pairWithCode("192.168.0.30:40002", "000000")
        assertEquals(PairResult.Failed("Failed: Wrong password or connection was dropped."), result)
    }

    @Test
    fun `ignores devices that are not the paired phone`(): Unit = runBlocking {
        var polls = 0
        val adb = Adb { args ->
            when (args.first()) {
                // The first lookup fails, so the USB phone already attached looks new; an emulator also appears.
                "devices" -> if (polls++ == 0) {
                    throw IOException(
                        "adb devices timed out",
                    )
                } else {
                    "List of devices attached\nR5CXUSB\tdevice\nemulator-5554\tdevice\n"
                }
                "pair" -> "Successfully paired to 192.168.0.30:40002 [guid=adb-R5CX123-AbCdEf]"
                else -> ""
            }
        }
        val result = WifiPairing(adb, { "x".repeat(it) }, {}).pairWithCode("192.168.0.30:40002", "123456")
        assertIs<PairResult.NotConnected>(result)
    }

    @Test
    fun `QR pairing sends the QR password`() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val adb = Adb { args ->
            calls += args
            when (args.first()) {
                "pair" -> "Successfully paired to 192.168.0.12:40001 [guid=adb-R5CX999-XyZ]"
                "devices" -> "List of devices attached\n" + if (calls.count { it.first() == "devices" } > 1) "192.168.0.12:37000\tdevice\n" else ""
                else -> ""
            }
        }
        val pairing = WifiPairing(adb, { "p".repeat(it) }, {})
        val result = pairing.pairWithQr(MdnsService("dari-pppppppp", "_adb-tls-pairing._tcp", "192.168.0.12:40001"))
        assertEquals(PairResult.Connected("192.168.0.12:37000"), result)
        assertTrue(listOf("pair", "192.168.0.12:40001", "pppppppppppp") in calls)
    }

    @Test
    fun `parses the guid from adb pair`() {
        assertEquals("adb-R5CX123-AbCdEf", WifiPairing.parseGuid("Successfully paired to 1.2.3.4:5 [guid=adb-R5CX123-AbCdEf]"))
        assertNull(WifiPairing.parseGuid("Successfully paired to 1.2.3.4:5"))
    }

    @Test
    fun `connects by hand to the paired host when adb does not`() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val adb = Adb { args ->
            calls += args
            when (args.first()) {
                "pair" -> "Successfully paired to 192.168.0.12:40001"
                "mdns" -> mdns
                "devices" -> "List of devices attached\n"
                else -> ""
            }
        }
        val result = WifiPairing(adb, { "x".repeat(it) }, {}).pairWithCode("192.168.0.12:40001", "123456")
        assertIs<PairResult.NotConnected>(result)
        assertTrue(listOf("connect", "192.168.0.12:37000") in calls)
        assertEquals(1, calls.count { it.firstOrNull() == "connect" })
    }

    @Test
    fun `the QR image decodes back to the payload`() {
        val payload = WifiPairing(Adb { "" }).qrPayload
        val matrix = encodeQr(payload)
        val source = object : LuminanceSource(matrix.width, matrix.height) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray =
                ByteArray(width) { x -> if (matrix[x, y]) 0 else -1 }
            override fun getMatrix(): ByteArray = ByteArray(width * height) { i -> if (matrix[i % width, i / width]) 0 else -1 }
        }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.PURE_BARCODE to true))
        assertEquals(payload, decoded.text)
    }
}
