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
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.easyhooon.dari.Dari
import com.easyhooon.dari.interceptor.DariInterceptor
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val interceptor: DariInterceptor? = Dari.createInterceptor()
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
                else -> {
                    val error = """{"error":"Unknown handler","handler":"$handlerName"}"""
                    interceptor?.onWebToAppResponse(handlerName, requestId, error, false)
                    callJs(requestId, false, error)
                }
            }
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

    @Suppress("DEPRECATION")
    private fun handleHapticFeedback(handlerName: String, requestId: String, data: String?) {
        val json = data?.let { JSONObject(it) }
        val durationMs = json?.optLong("duration", 50) ?: 50

        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE),
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
        val message = json?.optString("message", "Hello!") ?: "Hello!"
        val duration = if (json?.optString("duration") == "long") {
            Toast.LENGTH_LONG
        } else {
            Toast.LENGTH_SHORT
        }

        runOnUiThread { Toast.makeText(this, message, duration).show() }

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

        runOnUiThread { Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show() }

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
}