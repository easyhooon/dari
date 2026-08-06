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
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.easyhooon.dari.Dari
import com.easyhooon.dari.interceptor.ProtobufDariInterceptor
import com.easyhooon.dari.interceptor.ProtobufPayloadDecoder
import com.google.protobuf.StringValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val interceptor: ProtobufDariInterceptor? = Dari.createInterceptor(
        tag = "Sample",
        protobufDecoder = ProtobufPayloadDecoder { payload, context ->
            if (context.handlerName != PROTOBUF_HANDLER) return@ProtobufPayloadDecoder null

            JSONObject().apply {
                put("type", "google.protobuf.StringValue")
                put("value", StringValue.parseFrom(payload).value)
                put("part", context.part.name)
            }.toString(2)
        },
    )
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
            // Sample loads from `file:///android_asset/...` and exposes a
            // JavascriptInterface to the page. Without locking down the
            // file:// access flags the sample page (or anything that
            // navigates the WebView) could read other local files via
            // fetch('file:///...') and reach into other origins. Because
            // the sample is what people copy-paste, harden it. (CWE-749)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
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

    private fun callProtobufJs(requestId: String, success: Boolean, data: ByteArray) {
        val base64Data = Base64.encodeToString(data, Base64.NO_WRAP)
        webView?.post {
            webView?.evaluateJavascript(
                "javascript:onProtobufBridgeResponse('$requestId', $success, '$base64Data')",
                null,
            )
        }
    }

    inner class BridgeInterface {
        @RequiresApi(Build.VERSION_CODES.P)
        @JavascriptInterface
        fun onBridgeRequest(handlerName: String, requestId: String, data: String?) {
            val perCallFireAndForget = if (handlerName == "fireAndForgetPerCall") true else null
            interceptor?.onWebToAppRequest(handlerName, requestId, data, fireAndForget = perCallFireAndForget)

            when (handlerName) {
                "getAppInfo" -> handleGetAppInfo(requestId)
                "hapticFeedback" -> handleHapticFeedback(handlerName, requestId, data)
                "showToast" -> handleShowToast(handlerName, requestId, data)
                "shareText" -> handleShareText(handlerName, requestId, data)
                "copyToClipboard" -> handleCopyToClipboard(handlerName, requestId, data)
                "openAppSettings" -> handleOpenAppSettings(handlerName, requestId)
                "requestCameraPermission" -> handleRequestCameraPermission(requestId)
                "sendWithNullFields" -> handleSendWithNullFields(handlerName, requestId, data)
                "fetchTravelItinerary" -> handleFetchTravelItinerary(handlerName, requestId)
                "fetchLargeData" -> handleFetchLargeData(handlerName, requestId)
                "simulateSlowResponse" -> handleSimulateSlowResponse(handlerName, requestId)
                "simulateError" -> handleSimulateError(handlerName, requestId, data)
                "fireAndForgetPerCall" -> { /* auto-resolved to SUCCESS by Dari via fireAndForget=true */ }
                "noResponsePending" -> { /* intentionally no response — stays IN_PROGRESS */ }
                else -> {
                    val error = """{"error":"Unknown handler","handler":"$handlerName"}"""
                    interceptor?.onWebToAppResponse(handlerName, requestId, error, false)
                    callJs(requestId, false, error)
                }
            }
        }

        @JavascriptInterface
        fun onProtobufBridgeRequest(requestId: String, base64Data: String) {
            val requestData = Base64.decode(base64Data, Base64.NO_WRAP)
            interceptor?.onWebToAppProtobufRequest(PROTOBUF_HANDLER, requestId, requestData)

            val result = runCatching {
                val request = StringValue.parseFrom(requestData)
                StringValue.newBuilder()
                    .setValue("Android received: ${request.value}")
                    .build()
            }
            val response = result.getOrElse { error ->
                StringValue.newBuilder()
                    .setValue("Invalid protobuf: ${error.message}")
                    .build()
            }
            val success = result.isSuccess
            val responseData = response.toByteArray()

            interceptor?.onWebToAppProtobufResponse(
                PROTOBUF_HANDLER,
                requestId,
                responseData,
                success,
            )
            callProtobufJs(requestId, success, responseData)
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

    // Keep the complete payload together so its expandable shape stays easy to inspect.
    @Suppress("LongMethod")
    private fun handleFetchTravelItinerary(handlerName: String, requestId: String) {
        val response = JSONObject(
            """
            {
              "trip": {
                "id": "TRIP-2026-JEJU-03",
                "title": "Jeju Summer Escape",
                "destination": {
                  "city": "Jeju",
                  "country": "South Korea",
                  "coordinates": {
                    "latitude": 33.4996,
                    "longitude": 126.5312
                  }
                },
                "travelers": [
                  {
                    "name": "Mina",
                    "role": "planner",
                    "preferences": {
                      "seat": "window",
                      "meal": "vegetarian"
                    }
                  },
                  {
                    "name": "Joon",
                    "role": "photographer",
                    "preferences": {
                      "seat": "aisle",
                      "meal": "standard"
                    }
                  }
                ],
                "days": [
                  {
                    "day": 1,
                    "date": "2026-08-14",
                    "theme": "Ocean & Sunset",
                    "activities": [
                      {
                        "time": "10:30",
                        "title": "Check in at Aewol",
                        "location": {
                          "name": "Aewol Stay",
                          "district": "Aewol-eup"
                        },
                        "tags": ["stay", "ocean-view"],
                        "reservation": {
                          "status": "confirmed",
                          "confirmationCode": "AWL-4815"
                        }
                      },
                      {
                        "time": "18:40",
                        "title": "Sunset coastal walk",
                        "location": {
                          "name": "Handam Coastal Trail",
                          "district": "Aewol-eup"
                        },
                        "tags": ["sunset", "walking"],
                        "reservation": null
                      }
                    ]
                  },
                  {
                    "day": 2,
                    "date": "2026-08-15",
                    "theme": "Forest & Local Food",
                    "activities": [
                      {
                        "time": "09:00",
                        "title": "Walk through Bijarim Forest",
                        "location": {
                          "name": "Bijarim Forest",
                          "district": "Gujwa-eup"
                        },
                        "tags": ["forest", "nature"],
                        "reservation": {
                          "status": "not-required",
                          "confirmationCode": null
                        }
                      },
                      {
                        "time": "13:00",
                        "title": "Jeju seasonal table",
                        "location": {
                          "name": "Sorang Kitchen",
                          "district": "Seogwipo-si"
                        },
                        "tags": ["food", "local"],
                        "reservation": {
                          "status": "confirmed",
                          "confirmationCode": "SRG-1300"
                        }
                      }
                    ]
                  }
                ],
                "summary": {
                  "totalDays": 2,
                  "estimatedBudget": {
                    "amount": 780000,
                    "currency": "KRW"
                  },
                  "highlights": [
                    "ocean-view stay",
                    "coastal sunset",
                    "forest trail",
                    "local cuisine"
                  ]
                }
              }
            }
            """.trimIndent(),
        ).apply {
            put("requestId", requestId)
        }
        interceptor?.onWebToAppResponse(handlerName, requestId, response.toString(), true)
        callJs(requestId, true, """{"tripId":"TRIP-2026-JEJU-03","dayCount":2}""")
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

    private companion object {
        const val PROTOBUF_HANDLER = "protobufGreeting"
    }
}
