package com.easyhooon.dari.desktop.app.adb

import kotlinx.coroutines.delay
import java.io.IOException
import java.security.SecureRandom

sealed interface PairResult {
    data class Connected(val serial: String) : PairResult
    data class Failed(val message: String) : PairResult

    /** Paired, but no new device showed up in time. */
    data object NotConnected : PairResult
}

/** Phones advertising `_adb-tls-pairing`: ours after the QR was scanned, others on the pairing-code screen. */
data class PairingScan(val qr: MdnsService?, val codeRequests: List<MdnsService>)

/**
 * Android 11+ wireless debugging pairing, as Android Studio does it. Both modes look the same on the network:
 * the phone advertises `_adb-tls-pairing._tcp`, and the instance name tells a scanned QR (ours) from a pairing code.
 * One instance per dialog, so the QR password lives only as long as the dialog.
 */
class WifiPairing internal constructor(
    private val adb: Adb,
    token: (Int) -> String,
    private val pause: suspend (Long) -> Unit,
) {
    constructor(adb: Adb) : this(adb, ::randomToken, { delay(it) })

    // Alphanumerics only: `;`, `:`, `,`, `\` and `"` would need escaping in the WIFI: payload.
    val serviceName = "dari-" + token(SERVICE_LENGTH)
    private val password = token(PASSWORD_LENGTH)

    /** The payload Settings → Wireless debugging → Pair device with QR code expects. */
    val qrPayload = "WIFI:T:ADB;S:$serviceName;P:$password;;"

    fun scan(): PairingScan {
        val pairing = adb.mdnsServices().filter { it.isPairing }.distinctBy { it.name }
        return PairingScan(
            qr = pairing.firstOrNull { it.name == serviceName },
            codeRequests = pairing.filter { it.name != serviceName },
        )
    }

    suspend fun pairWithQr(service: MdnsService): PairResult = pair(service.address, password)

    suspend fun pairWithCode(address: String, code: String): PairResult = pair(address.trim(), code.trim())

    private suspend fun pair(address: String, secret: String): PairResult {
        // Unknown when the lookup fails; then only serials matching the paired phone count.
        val before = runCatching { adb.onlineSerials() }.getOrNull()
        val output = try {
            adb.pair(address, secret)
        } catch (e: IOException) {
            return PairResult.Failed(lastLine(e.message) ?: "Pairing failed.")
        }
        if (!output.contains("Successfully paired")) return PairResult.Failed(lastLine(output) ?: "Pairing failed.")
        // adb usually connects on its own once the phone re-advertises `_adb-tls-connect`, as
        // `<guid>._adb-tls-connect._tcp`; a manual connect gives `<host>:<port>`. Other new serials are other devices.
        val host = address.substringBeforeLast(':')
        val guid = parseGuid(output)
        fun isPairedPhone(serial: String) = (guid != null && serial.startsWith(guid)) || serial.startsWith("$host:")
        repeat(CONNECT_ATTEMPTS) { attempt ->
            val now = runCatching { adb.onlineSerials() }.getOrDefault(emptySet())
            now.firstOrNull { it !in before.orEmpty() && isPairedPhone(it) }?.let { return PairResult.Connected(it) }
            // Once only: connecting by hand while adb auto-connects would list the phone twice.
            if (attempt == AUTO_CONNECT_GRACE) {
                runCatching { adb.mdnsServices() }.getOrDefault(emptyList())
                    .firstOrNull { it.isConnect && it.host == host && it.address !in now }
                    ?.let { runCatching { adb.connect(it.address) } }
            }
            pause(POLL_MS)
        }
        return PairResult.NotConnected
    }

    companion object {
        const val POLL_MS = 1_000L
        private const val CONNECT_ATTEMPTS = 30
        private const val AUTO_CONNECT_GRACE = 8
        private const val SERVICE_LENGTH = 8
        private const val PASSWORD_LENGTH = 12
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val random = SecureRandom()

        private fun randomToken(length: Int): String = buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

        // `Successfully paired to 192.168.0.30:40002 [guid=adb-R5CX123-AbCdEf]`
        internal fun parseGuid(output: String): String? = Regex("""\[guid=([^\]\s]+)]""").find(output)?.groupValues?.get(1)

        private fun lastLine(text: String?): String? = text?.lineSequence()?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
    }
}
