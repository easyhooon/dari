package com.easyhooon.dari.sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.easyhooon.dari.Dari
import com.easyhooon.dari.interceptor.DariInterceptor
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val interceptor: DariInterceptor? = Dari.createInterceptor(tag = "SampleWebView")
    private var webView: WebView? = null

    // Pending camera permission request info
    private var pendingPermissionRequestId: String? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val requestId = pendingPermissionRequestId ?: return@registerForActivityResult
        val response = JSONObject().apply {
            put("permission", "camera")
            put("granted", isGranted)
        }
        interceptor?.onWebToAppResponse(
            "requestCameraPermission", requestId, response.toString(2), isGranted,
        )
        callJs(requestId, isGranted, response.toString())
        pendingPermissionRequestId = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(BridgeInterface(), "Android")
            loadUrl("file:///android_asset/sample.html")
        }

        setContentView(webView)
    }

    private fun callJs(requestId: String, success: Boolean, data: String) {
        val escaped = data.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        webView?.post {
            webView?.evaluateJavascript(
                "javascript:onBridgeResponse('$requestId', $success, '$escaped')",
                null,
            )
        }
    }

    inner class BridgeInterface {
        @RequiresApi(Build.VERSION_CODES.P)
        @JavascriptInterface
        fun onBridgeRequest(handlerName: String, requestId: String, data: String?) {
            interceptor?.onWebToAppRequest(handlerName, requestId, data)

            when (handlerName) {
                "getAppInfo" -> handleGetAppInfo(requestId)
                "hapticFeedback" -> handleHapticFeedback(handlerName, requestId, data)
                "showToast" -> handleShowToast(handlerName, requestId, data)
                "shareText" -> handleShareText(handlerName, requestId, data)
                "copyToClipboard" -> handleCopyToClipboard(handlerName, requestId, data)
                "openAppSettings" -> handleOpenAppSettings(handlerName, requestId)
                "requestCameraPermission" -> handleRequestCameraPermission(requestId)
                "sendWithNullFields" -> handleSendWithNullFields(handlerName, requestId, data)
                "fetchLargeData" -> handleFetchLargeData(handlerName, requestId)
                "simulateSlowResponse" -> handleSimulateSlowResponse(handlerName, requestId)
                "simulateError" -> handleSimulateError(handlerName, requestId, data)
                else -> {
                    val error = """{"error":"Unknown handler","handler":"$handlerName"}"""
                    interceptor?.onWebToAppResponse(handlerName, requestId, error, false)
                    callJs(requestId, false, error)
                }
            }
        }

        /**
         * Fire-and-forget bridge request without requestId.
         * No response will be sent back to the web side.
         */
        @JavascriptInterface
        fun onBridgeRequestFireAndForget(handlerName: String, data: String?) {
            // Pass null as requestId for fire-and-forget messages
            interceptor?.onWebToAppRequest(handlerName, null, data)

            when (handlerName) {
                "logEvent" -> handleLogEvent(data)
                "trackScreenView" -> handleTrackScreenView(data)
            }
            // No response - fire-and-forget
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun handleGetAppInfo(requestId: String) {
        val response = JSONObject().apply {
            put("appVersion", "${packageManager.getPackageInfo(packageName, 0).versionName}")
            put("appVersionCode", packageManager.getPackageInfo(packageName, 0).longVersionCode)
            put("osVersion", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("deviceBrand", Build.BRAND)
        }
        interceptor?.onWebToAppResponse("getAppInfo", requestId, response.toString(2), true)
        callJs(requestId, true, response.toString())
    }

    private fun handleHapticFeedback(handlerName: String, requestId: String, data: String?) {
        val json = data?.let { JSONObject(it) }
        val durationMs = json?.optLong("duration", 50) ?: 50

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        }
        vibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
        )

        val response = JSONObject().apply {
            put("vibrated", true)
            put("durationMs", durationMs)
        }
        interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(2), true)
        callJs(requestId, true, response.toString())
    }

    private fun handleShowToast(handlerName: String, requestId: String, data: String?) {
        val json = data?.let { JSONObject(it) }
        val rawMessage = json?.optString("message", "Hello!") ?: "Hello!"
        val prefix = interceptor?.tag?.let { "[$it] " } ?: ""
        val message = "$prefix$rawMessage"
        val duration = if (json?.optString("duration") == "long") {
            Toast.LENGTH_LONG
        } else {
            Toast.LENGTH_SHORT
        }

        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, message, duration).show()
        }

        val response = JSONObject().apply { put("shown", true) }
        interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(2), true)
        callJs(requestId, true, response.toString())
    }

    private fun handleShareText(handlerName: String, requestId: String, data: String?) {
        val json = data?.let { JSONObject(it) }
        val title = json?.optString("title", "Share") ?: "Share"
        val text = json?.optString("text", "") ?: ""

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))

        val response = JSONObject().apply { put("shared", true) }
        interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(2), true)
        callJs(requestId, true, response.toString())
    }

    private fun handleCopyToClipboard(handlerName: String, requestId: String, data: String?) {
        val json = data?.let { JSONObject(it) }
        val text = json?.optString("text", "") ?: ""

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("dari", text))

        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        val response = JSONObject().apply {
            put("copied", true)
            put("length", text.length)
        }
        interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(2), true)
        callJs(requestId, true, response.toString())
    }

    private fun handleOpenAppSettings(handlerName: String, requestId: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            this.data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)

        val response = JSONObject().apply { put("opened", true) }
        interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(2), true)
        callJs(requestId, true, response.toString())
    }

    private fun handleRequestCameraPermission(requestId: String) {
        pendingPermissionRequestId = requestId
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun handleSendWithNullFields(handlerName: String, requestId: String, data: String?) {
        val json = data?.let { JSONObject(it) }
        val name = json?.optString("name")?.takeIf { it.isNotEmpty() } ?: "unknown"

        val response = JSONObject().apply {
            put("name", name)
            put("processedAt", System.currentTimeMillis())
            put("profileImage", JSONObject.NULL)
            put("nickname", JSONObject.NULL)
            put("metadata", JSONObject.NULL)
        }
        interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(2), true)
        callJs(requestId, true, response.toString())
    }

    private fun handleFetchLargeData(handlerName: String, requestId: String) {
        // Simulate a large response payload (~2MB) that exceeds Android's Binder transaction limit.
        // Dari will automatically truncate this data based on maxContentLength config.
        val largePayload = buildString {
            append("""{"items":[""")
            for (i in 0 until 10_000) {
                if (i > 0) append(",")
                append("""{"id":$i,"title":"Item #$i","description":"This is a sample item with some additional data to simulate a realistic large API response payload.","category":"category_${i % 20}","price":${i * 1.5},"inStock":${i % 3 != 0},"tags":["tag_${i % 5}","tag_${i % 7}","tag_${i % 11}"]}""")
            }
            append("]}")
        }
        interceptor?.onWebToAppResponse(handlerName, requestId, largePayload, true)
        callJs(requestId, true, """{"size":${largePayload.length},"itemCount":10000}""")
    }

    private fun handleSimulateSlowResponse(handlerName: String, requestId: String) {
        lifecycleScope.launch {
            delay(10_000)
            val response = JSONObject().apply {
                put("result", "completed after 10s delay")
            }
            interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(2), true)
            callJs(requestId, true, response.toString())
        }
    }

    private fun handleSimulateError(handlerName: String, requestId: String, data: String?) {
        val errorType = runCatching {
            data?.let { JSONObject(it).optString("errorType", "generic") }
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "generic"
        val response = JSONObject().apply {
            put("error", errorType)
            put("message", "Simulated $errorType error for testing")
        }
        interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(2), false)
        callJs(requestId, false, response.toString())
    }

    private fun handleLogEvent(data: String?) {
        val json = data?.let { JSONObject(it) }
        val event = json?.optString("event", "unknown") ?: "unknown"
        val screen = json?.optString("screen", "unknown") ?: "unknown"
        // In a real app, this would send to analytics service
        android.util.Log.d("Dari-Sample", "Analytics event: $event on $screen")
    }

    private fun handleTrackScreenView(data: String?) {
        val json = data?.let { JSONObject(it) }
        val screen = json?.optString("screen", "unknown") ?: "unknown"
        val timestamp = json?.optLong("timestamp", 0) ?: 0
        // In a real app, this would send to analytics service
        android.util.Log.d("Dari-Sample", "Screen view: $screen at $timestamp")
    }
}
