# Dari

[![Maven Central](https://img.shields.io/maven-central/v/io.github.easyhooon/dari)](https://central.sonatype.com/artifact/io.github.easyhooon/dari)
[![Documentation](https://img.shields.io/badge/docs-easyhooon.github.io%2Fdari-blue)](https://easyhooon.github.io/dari)

**Dari** (다리) means **"bridge"** in Korean. Dari is an Android library for inspecting WebView bridge communication in real time, inspired by [Chucker](https://github.com/ChuckerTeam/chucker).

Just as Chucker intercepts and displays HTTP traffic, Dari captures and visualizes **WebView JavaScript bridge messages** between your web content and native Android code.

📖 **[Documentation](https://easyhooon.github.io/dari)** — Setup guide, configuration reference, and API docs.

## Screenshots

<table>
  <tr>
    <td align="center"><b>Launcher Shortcut</b></td>
    <td align="center"><b>Notification</b></td>
    <td align="center"><b>Message List</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/shortcut_v3.jpeg" width="270" /></td>
    <td><img src="screenshots/notification.jpeg" width="270" /></td>
    <td><img src="screenshots/list_v2.jpeg" width="270" /></td>
  </tr>
  <tr>
    <td align="center"><b>Overview</b></td>
    <td align="center"><b>Request</b></td>
    <td align="center"><b>Response</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/overview_v2.jpeg" width="270" /></td>
    <td><img src="screenshots/request_v2.jpeg" width="270" /></td>
    <td><img src="screenshots/response_v2.jpeg" width="270" /></td>
  </tr>
  <tr>
    <td align="center"><b>Tag Filter</b></td>
    <td align="center"><b>Settings</b></td>
    <td align="center"><b>Dark Mode</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/tag_filter.jpeg" width="270" /></td>
    <td><img src="screenshots/share_bottom_sheet.jpeg" width="270" /></td>
    <td><img src="screenshots/dark_mode.jpeg" width="270" /></td>
  </tr>
</table>

## Features

- Intercepts Web-to-App and App-to-Web bridge messages
- **Tag support** for identifying message sources in multi-WebView/Activity environments
- **Message status tracking** — `IN_PROGRESS`, `SUCCESS`, `ERROR` with color-coded indicators
- **Status filtering** — filter messages by status via chips
- Chucker-style persistent notification showing recent bridge activity
- Message list UI with search, filter by handler name, and **tag-based filtering**
- Detail view with Overview / Request / Response tabs
- JSON pretty-printing for request and response payloads
- Export messages as text or JSON
- **Shake-to-open** — shake device to launch Dari UI (with haptic feedback)
- **Dark mode** — System / Light / Dark theme toggle
- **Settings bottom sheet** — shake toggle, theme selector, clear data, and version info
- Dynamic launcher shortcut for quick access
- Zero overhead in release builds via no-op module
- Auto-initialization via `androidx.startup`

## Setup

### Gradle

Add the `dari` (debug) and `dari-noop` (release) dependencies to your app's `build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation("io.github.easyhooon:dari:<latest-version>")
    releaseImplementation("io.github.easyhooon:dari-noop:<latest-version>")
}
```

That's it for initialization. Dari auto-initializes via `androidx.startup` when your app starts.

### Interceptor Integration

Dari requires **manual injection** of the interceptor into your WebView bridge layer. Create an interceptor instance and call its methods at the appropriate points in your bridge communication code.

#### 1. Create the interceptor

```kotlin
import com.easyhooon.dari.Dari
import com.easyhooon.dari.interceptor.DariInterceptor

// Basic usage (no tag)
val interceptor: DariInterceptor? = Dari.createInterceptor()

// With tag — identifies the source in multi-WebView/Activity environments
val paymentInterceptor: DariInterceptor? = Dari.createInterceptor(tag = "PaymentWebView")
val mainInterceptor: DariInterceptor? = Dari.createInterceptor(tag = "MainWebView")
```

When a `tag` is provided, all messages captured by that interceptor are automatically tagged. Tags appear as chips in the message list and can be used to filter messages by source.

#### 2. Intercept Web-to-App messages

Call the interceptor in your `@JavascriptInterface` method when a request arrives and after the response is ready:

```kotlin
// When a request is received from JavaScript
interceptor?.onWebToAppRequest(handlerName, requestId, data)

// After processing, log the response
interceptor?.onWebToAppResponse(handlerName, requestId, responseData, isSuccess)
```

#### 3. Intercept App-to-Web messages

Call the interceptor when your native code sends a message to JavaScript and when the response comes back:

```kotlin
// When sending a message to JavaScript
interceptor?.onAppToWebRequest(handlerName, requestId, data)

// When the web response is received
interceptor?.onAppToWebResponse(requestId, isSuccess, responseData)
```

#### 4. Fire-and-Forget messages (optional requestId)

For messages that don't require a response (e.g., analytics events, screen tracking), you can pass `null` as the `requestId`:

```kotlin
// Fire-and-forget: no response expected
interceptor?.onWebToAppRequest(handlerName, null, data)
// No need to call onWebToAppResponse
```

When `requestId` is `null`, the message is treated as standalone and won't be matched with a response.

### Custom Configuration

You can customize Dari by calling `init` with a config before auto-initialization occurs, or in your `Application.onCreate()`:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Dari.init(
            context = this,
            config = DariConfig(
                maxEntries = 1000,              // Default: 500
                showNotification = true,        // Default: true
                maxContentLength = 500_000,     // Default: 500,000 (truncate large payloads)
                shakeToOpen = true,             // Default: false
                retentionPeriod = 1.days,       // Default: null (disabled)
            )
        )
    }
}
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `maxEntries` | `Int` | `500` | Maximum number of stored messages |
| `showNotification` | `Boolean` | `true` | Show persistent notification |
| `maxContentLength` | `Int` | `500,000` | Truncate request/response payloads exceeding this length |
| `shakeToOpen` | `Boolean` | `false` | Enable shake gesture to open Dari UI |
| `retentionPeriod` | `Duration?` | `null` | TTL-based message cleanup (e.g., `1.hours`, `3.days`). `null` disables it |

## Module Structure

```
dari/
├── dari-core/     ← Shared types (DariConfig, MessageEntry, DariInterceptor, etc.)
├── dari/          ← Debug library (interceptor, UI, notifications)
├── dari-noop/     ← Release no-op (same API surface, zero overhead)
└── sample/        ← Sample app with WebView bridge demo
```

## Sample App

The `sample/` module contains a working WebView demo with realistic bridge scenarios: fetching app/device info, showing toast messages, haptic feedback, sharing via native share sheet, clipboard access, opening app settings, and requesting camera permission. Run the sample app, tap the buttons, and observe:

1. A notification appears showing recent bridge messages
2. Tap the notification to open the message list
3. Tap any message to see its details (overview, request payload, response payload)

## API Reference

### Dari

| Method | Description |
|--------|-------------|
| `init(context, config)` | Initialize with custom configuration |
| `createInterceptor(tag?)` | Create a `DariInterceptor` with an optional tag (returns `null` in noop) |
| `setShakeToOpenEnabled(enabled)` | Enable/disable shake-to-open at runtime (persisted) |
| `setDarkMode(value)` | Override dark mode: `true` / `false` / `null` (system default). Persisted |
| `showNotification()` | Show the notification (e.g., after permission grant) |
| `clear()` | Clear all stored messages and dismiss notification |

### DariInterceptor

| Method | Description |
|--------|-------------|
| `onWebToAppRequest()` | Log a Web-to-App request. `requestId` is optional for fire-and-forget messages. |
| `onWebToAppResponse()` | Log the response to a Web-to-App request. Skipped if `requestId` is null. |
| `onAppToWebRequest()` | Log an App-to-Web message. `requestId` is optional for fire-and-forget messages. |
| `onAppToWebResponse()` | Log the response to an App-to-Web message. Skipped if `requestId` is null. |

```kotlin
/**
 * Interface for intercepting WebView bridge communication.
 * Injected into WebViewBridge to capture all bridge messages.
 */
interface DariInterceptor {
    /** Called when a Web -> App request is received */
    fun onWebToAppRequest(
        handlerName: String,
        requestId: String?,
        requestData: String?,
    )

    /** Called when a response is sent for a Web -> App request */
    fun onWebToAppResponse(
        handlerName: String,
        requestId: String?,
        responseData: String?,
        isSuccess: Boolean,
    )

    /** Called when an App -> Web request is sent */
    fun onAppToWebRequest(
        handlerName: String,
        requestId: String?,
        data: String?,
    )

    /** Called when a web response is received for an App -> Web request */
    fun onAppToWebResponse(
        requestId: String?,
        isSuccess: Boolean,
        responseData: String?,
    )
}
```

## License

```
Copyright 2026 easyhooon

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
