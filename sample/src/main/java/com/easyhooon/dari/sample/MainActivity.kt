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
import com.easyhooon.dari.interceptor.protobufDecoder
import com.google.protobuf.StringValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {

    private val interceptor: ProtobufDariInterceptor? = Dari.createInterceptor(
        tag = "Sample",
        protobufDecoder = protobufDecoder {
            webToApp(PROTOBUF_HANDLER) {
                request { describeStringValue(it, "REQUEST") }
                response { describeStringValue(it, "RESPONSE") }
            }
        },
    )
    private var webView: WebView? = null
    private val appRequestCounter = AtomicInteger()

    @Volatile
    private var pageLoaded = false

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
        sendResponseToWeb("requestCameraPermission", requestId, isGranted, response.toString())
        pendingPermissionRequestId = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extra masking for the desktop app, after the built-in one when that is on; a no-op in dari-noop.
        Dari.setPayloadRedactor { entry ->
            entry.copy(requestData = entry.requestData?.replace(Regex("\"userId\":\"[^\"]*\""), "\"userId\":\"***\""))
        }
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
            webViewClient = object : WebViewClient() {
                // Also fires on SPA history changes, so each request records the page it came from.
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    interceptor?.onPageUrlChanged(url)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    pageLoaded = true
                    // App-initiated round trip: the page answers through BridgeInterface.onAppToWebResponse.
                    sendToWeb("getPageState", nextAppRequestId(), null)
                }
            }
            addJavascriptInterface(BridgeInterface(), "Android")
            loadUrl("file:///android_asset/sample.html")
        }

        setContentView(webView)
    }

    override fun onResume() {
        super.onResume()
        // Fire-and-forget App-to-Web notification; the page sends nothing back.
        if (pageLoaded) sendToWeb("appResumed", null, """{"at":${System.currentTimeMillis()}}""")
    }

    // Every App-to-Web message goes through here; a null requestId means the page won't answer.
    private fun sendToWeb(handlerName: String, requestId: String?, data: String?) {
        interceptor?.onAppToWebRequest(handlerName, requestId, data, fireAndForget = if (requestId == null) true else null)
        val id = requestId?.let { "'$it'" } ?: "null"
        val payload = data?.let { "'${it.escapeForJs()}'" } ?: "null"
        webView?.post {
            webView?.evaluateJavascript("onAppRequest('$handlerName', $id, $payload)", null)
        }
    }

    private fun nextAppRequestId(): String = "app_${appRequestCounter.incrementAndGet()}"

    // Every Web-to-App reply goes through here, so this one call logs all responses.
    private fun sendResponseToWeb(handlerName: String, requestId: String, success: Boolean, data: String) {
        interceptor?.onWebToAppResponse(handlerName, requestId, data, success)
        val escaped = data.escapeForJs()
        webView?.post {
            webView?.evaluateJavascript(
                "javascript:onBridgeResponse('$requestId', $success, '$escaped')",
                null,
            )
        }
    }

    private fun sendProtobufResponseToWeb(requestId: String, success: Boolean, data: ByteArray) {
        interceptor?.onWebToAppProtobufResponse(PROTOBUF_HANDLER, requestId, data, success)
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
            // Every request enters here, so one call logs them all before dispatching.
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
                "signIn" -> handleSignIn(handlerName, requestId)
                "fetchLargeData" -> handleFetchLargeData(handlerName, requestId)
                "simulateSlowResponse" -> handleSimulateSlowResponse(handlerName, requestId)
                "simulateError" -> handleSimulateError(handlerName, requestId, data)
                "fireAndForgetPerCall" -> { /* auto-resolved to SUCCESS by Dari via fireAndForget=true */ }
                "noResponsePending" -> { /* intentionally no response — stays IN_PROGRESS */ }
                else -> {
                    val error = """{"error":"Unknown handler","handler":"$handlerName"}"""
                    sendResponseToWeb(handlerName, requestId, false, error)
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
            sendProtobufResponseToWeb(requestId, success, response.toByteArray())
        }

        // Lets the page trigger an App-to-Web call on demand; the trigger itself is not a bridge message.
        @JavascriptInterface
        fun requestAppToWeb(handlerName: String) {
            sendToWeb(handlerName, nextAppRequestId(), """{"requestedAt":${System.currentTimeMillis()}}""")
        }

        // Every web reply to an App-to-Web request enters here.
        @JavascriptInterface
        fun onAppToWebResponse(requestId: String, success: Boolean, data: String?) {
            interceptor?.onAppToWebResponse(requestId, success, data)
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
        sendResponseToWeb("getAppInfo", requestId, true, response.toString())
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
        sendResponseToWeb(handlerName, requestId, true, response.toString())
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
        sendResponseToWeb(handlerName, requestId, true, response.toString())
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
        sendResponseToWeb(handlerName, requestId, true, response.toString())
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
        sendResponseToWeb(handlerName, requestId, true, response.toString())
    }

    private fun handleOpenAppSettings(handlerName: String, requestId: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            this.data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)

        val response = JSONObject().apply { put("opened", true) }
        sendResponseToWeb(handlerName, requestId, true, response.toString())
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
        sendResponseToWeb(handlerName, requestId, true, response.toString())
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
        sendResponseToWeb(handlerName, requestId, true, largePayload)
    }

    // Fake credentials so "Mask sensitive values" has something to hide.
    private fun handleSignIn(handlerName: String, requestId: String) {
        val response = JSONObject().apply {
            put("accessToken", "at_sample_7f3c9a")
            put("refreshToken", "rt_sample_2b8e1d")
            put("expiresIn", 3600)
            put("cookie", "SID=sample-session; HttpOnly")
            put(
                "profile",
                JSONObject().apply {
                    put("name", "Test User")
                    put("email", "test@example.com")
                },
            )
        }
        sendResponseToWeb(handlerName, requestId, true, response.toString())
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
        sendResponseToWeb(handlerName, requestId, true, response.toString())
    }

    private fun handleSimulateSlowResponse(handlerName: String, requestId: String) {
        lifecycleScope.launch {
            delay(10_000)
            val response = JSONObject().apply {
                put("result", "completed after 10s delay")
            }
            sendResponseToWeb(handlerName, requestId, true, response.toString())
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
        sendResponseToWeb(handlerName, requestId, false, response.toString())
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

        fun String.escapeForJs(): String = replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

        fun describeStringValue(payload: ByteArray, part: String): String =
            JSONObject().apply {
                put("type", "google.protobuf.StringValue")
                put("value", StringValue.parseFrom(payload).value)
                put("part", part)
            }.toString(2)
    }
}
