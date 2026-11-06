package com.easyhooon.dari.desktop.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.easyhooon.dari.desktop.app.Appearance
import com.easyhooon.dari.desktop.app.adb.MdnsService
import com.easyhooon.dari.desktop.app.adb.PairResult
import com.easyhooon.dari.desktop.app.adb.WifiPairing
import com.easyhooon.dari.desktop.app.ui.theme.Dari
import com.easyhooon.dari.desktop.app.ui.theme.DariTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface PairingStep {
    data object Waiting : PairingStep
    data class Pairing(val target: String) : PairingStep
    data class Done(val result: PairResult) : PairingStep
}

/** Session → Pair Device over Wi-Fi: Android 11+ wireless debugging pairing by QR code or pairing code. */
@Composable
fun WifiPairingWindow(newPairing: () -> WifiPairing, appearance: Appearance, onClose: () -> Unit) {
    // A fresh name and password each time the window opens; closing it drops both.
    var pairing by remember { mutableStateOf(newPairing()) }
    var step by remember { mutableStateOf<PairingStep>(PairingStep.Waiting) }
    var codeRequests by remember { mutableStateOf(emptyList<MdnsService>()) }
    var waitedSeconds by remember { mutableStateOf(0) }
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun run(target: String, block: suspend () -> PairResult) {
        // A double click or the scan loop finding the QR at the same time must not pair twice.
        if (step != PairingStep.Waiting) return
        step = PairingStep.Pairing(target)
        scope.launch { step = PairingStep.Done(withContext(Dispatchers.IO) { block() }) }
    }

    LaunchedEffect(pairing) {
        waitedSeconds = 0
        while (step == PairingStep.Waiting) {
            val scan = withContext(Dispatchers.IO) { runCatching { pairing.scan() }.getOrNull() }
            if (step != PairingStep.Waiting) break
            codeRequests = scan?.codeRequests.orEmpty()
            val qr = scan?.qr
            if (qr != null) {
                run(qr.address) { pairing.pairWithQr(qr) }
                break
            }
            delay(WifiPairing.POLL_MS)
            waitedSeconds++
        }
    }

    Window(
        onCloseRequest = onClose,
        onPreviewKeyEvent = { handleCloseWindowShortcut(it, onClose = onClose) },
        title = "Pair Device over Wi-Fi",
        state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), width = 640.dp, height = 520.dp),
    ) {
        CloseWindowMenu(onClose)
        WindowChrome(appearance)
        DariTheme(appearance) {
            Column(
                Modifier.fillMaxSize().background(Dari.colors.background).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Android 11 or later, on the same network as this computer. " +
                        "On the phone, open Developer options → Wireless debugging.",
                    style = Dari.type.caption,
                    color = Dari.colors.textSecondary,
                )
                when (val current = step) {
                    PairingStep.Waiting -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QrCode(pairing.qrPayload)
                            Text("Pair device with QR code", style = Dari.type.label, color = Dari.colors.text)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Scan it from Wireless debugging → Pair device with QR code. The camera app " +
                                    "reads it as a Wi-Fi network and drops the phone's current one.",
                                style = Dari.type.caption,
                                color = Dari.colors.textSecondary,
                            )
                            Text("Or pair with a code", style = Dari.type.label, color = Dari.colors.text, modifier = Modifier.padding(top = 8.dp))
                            if (codeRequests.isEmpty()) {
                                Text(
                                    "Tap Pair device with pairing code on the phone; it shows up here.",
                                    style = Dari.type.caption,
                                    color = Dari.colors.textTertiary,
                                )
                            } else {
                                // Several phones can wait on shared Wi-Fi, so the user picks one; never auto-pick.
                                codeRequests.forEach { request ->
                                    DariMenuItem("${request.name}  ${request.address}", onClick = { address = request.address })
                                }
                            }
                            SearchField(
                                value = address,
                                onValueChange = { address = it },
                                placeholder = "IP address and port, e.g. 192.168.0.12:37123",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SearchField(
                                    value = code,
                                    onValueChange = { input -> code = input.filter(Char::isDigit).take(PAIRING_CODE_LENGTH) },
                                    placeholder = "6-digit code",
                                    modifier = Modifier.width(120.dp),
                                )
                                AccentButton("Pair", onClick = {
                                    if (address.isNotBlank() && code.length == PAIRING_CODE_LENGTH) {
                                        val target = address
                                        val secret = code
                                        run(target) { pairing.pairWithCode(target, secret) }
                                    }
                                })
                            }
                            if (waitedSeconds >= MDNS_HINT_SECONDS && codeRequests.isEmpty()) {
                                Text(
                                    "Nothing found yet. Some networks block device discovery (mDNS); type the address " +
                                        "and code from the phone's pairing-code screen instead. On macOS, restarting adb with " +
                                        "ADB_MDNS_OPENSCREEN=0 (Bonjour) can help.",
                                    style = Dari.type.caption,
                                    color = Dari.colors.warning,
                                )
                            }
                        }
                    }
                    is PairingStep.Pairing -> Text("Pairing with ${current.target}…", style = Dari.type.body, color = Dari.colors.text)
                    // Buttons at the bottom right, as in other dialogs; the window is sized for the QR step.
                    is PairingStep.Done -> Column(Modifier.weight(1f).fillMaxWidth()) {
                        val (message, color) = when (val result = current.result) {
                            is PairResult.Connected -> "Paired and connected. The phone now appears in the device list." to Dari.colors.success
                            PairResult.NotConnected ->
                                "Paired, but the device didn't connect. Toggle Wireless debugging off and on, then wait a few seconds." to
                                    Dari.colors.warning
                            is PairResult.Failed -> "Pairing failed: ${result.message}" to Dari.colors.danger
                        }
                        Text(message, style = Dari.type.body, color = color)
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            if (current.result !is PairResult.Connected) {
                                QuietButton("Try Again", onClick = {
                                    code = ""
                                    step = PairingStep.Waiting
                                    pairing = newPairing()
                                })
                            }
                            AccentButton("Done", onClick = onClose)
                        }
                    }
                }
            }
        }
    }
}

/** Dark modules on white with a quiet zone in both themes; scanners fail on inverted codes. */
@Composable
private fun QrCode(payload: String) {
    val matrix = remember(payload) { encodeQr(payload) }
    Canvas(Modifier.size(QR_SIZE.dp).background(Color.White)) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
    }
}

internal fun encodeQr(payload: String): BitMatrix = QRCodeWriter().encode(
    payload,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(EncodeHintType.MARGIN to QR_QUIET_ZONE, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L),
)

private const val QR_SIZE = 220
private const val QR_QUIET_ZONE = 4
private const val MDNS_HINT_SECONDS = 15
private const val PAIRING_CODE_LENGTH = 6
