# Dari for iOS

Records WKWebView bridge traffic and shows it in the Dari desktop app. There is no in-app inspector screen on iOS.

Status: Simulator and USB connections work. iOS connections are macOS-only by design: building and installing an iOS debug build already needs a Mac. Wi-Fi connections are not offered.

The full integration guide, including Staging builds and troubleshooting, is in [`documentation/content/docs/ios/`](../documentation/content/docs/ios/): setup in `index.mdx`, the capture methods in `capture.mdx`, and connection and troubleshooting in `connection.mdx`. `sample-ios/` has a runnable sample app.

## Install

Add `https://github.com/easyhooon/dari` as a Swift package (from `3.0.0`) and link the `Dari` product to your app target. The root `Package.swift` exposes only the `Dari` and `DariMacros` libraries; this folder's manifest adds the tests and the loopback demo.

## Integrate

Start Dari only in builds you inspect (Debug, or a Staging configuration with its own compilation condition). While it runs, anything that can reach the device's loopback can read the recorded messages.

```swift
import Dari

#if DEBUG
try? Dari.shared.start()
#endif
```

Create one interceptor per WKWebView. Then call it where your bridge receives and answers messages:

```swift
let dari = Dari.shared.interceptor(tag: "main")

// WKNavigationDelegate
dari.onPageUrlChanged(webView.url?.absoluteString)

// Web → App
func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
    dari.onWebToAppRequest(handlerName: message.name, requestId: callbackId, requestData: json)
    // … handle, then reply
    dari.onWebToAppResponse(handlerName: message.name, requestId: callbackId, responseData: result, isSuccess: true)
}

// App → Web
dari.onAppToWebRequest(handlerName: "onResume", requestId: id, data: payload)
webView.evaluateJavaScript(script) { value, error in
    dari.onAppToWebResponse(requestId: id, isSuccess: error == nil, responseData: value.map { "\($0)" })
}
```

Or let Dari hook WebKit instead (3.1.0+). Start it before creating web views; see the [iOS guide](https://easyhooon.github.io/dari/docs/ios/capture#automatic-capture) for custom field mapping:

```swift
try? Dari.shared.start(DariConfiguration(autoCapture: DariAutoCapture(tag: "main")))
```

Or record message handlers at compile time with the `DariMacros` product (3.2.0+), with no runtime hooks; see the [iOS guide](https://easyhooon.github.io/dari/docs/ios/capture#compile-time-capture-with-macros):

```swift
import DariMacros

@DariBridge(tag: "main")
final class Bridge: NSObject, WKScriptMessageHandler { … }
```

Requests and responses are paired by `requestId`, the same way as on Android. A nil `requestId`, or `fireAndForget: true`, records a message that is complete as soon as it's sent.

## Connect

1. Run the app in a Simulator, or on an iPhone or iPad attached over USB.
2. Open the Dari desktop app on the same Mac. Simulator apps appear under the **iOS Simulator** device; USB devices appear under their own name, reached through usbmuxd.

Each app listens on the first free port in `Dari.defaultPorts` (127.0.0.1:38670–38677), so up to eight Dari apps can run in Simulators at once.

## Develop

```bash
swift test
```

To check the desktop client against this SDK over loopback:

```bash
swift build --product DariLoopbackDemo
../gradlew -p .. :dari-desktop-app:test --tests '*IosLoopbackEndToEndTest' -Pdari.iosDemo="$(swift build --show-bin-path)/DariLoopbackDemo"
```

`Tests/DariTests/Fixtures` is generated from the Kotlin protocol. After an intended wire change, regenerate it with `./gradlew :dari-inspector-protocol:test -Pdari.updateIosFixtures`.
