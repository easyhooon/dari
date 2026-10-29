# Dari for iOS (preview)

Records WKWebView bridge traffic and shows it in the Dari desktop app. There is no in-app inspector screen on iOS.

Status: Simulator connections work. USB connections to a physical iPhone are not supported yet (#255).

The full integration guide, including Staging builds and troubleshooting, is in [`documentation/content/docs/ios.mdx`](../documentation/content/docs/ios.mdx). `sample-ios/` has a runnable sample app.

## Install

Add this repository as a Swift package and link the `Dari` product to your app target. Swift Package Manager needs the package at the repository root, so for now point Xcode at this folder as a local package.

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

Requests and responses are paired by `requestId`, the same way as on Android. A nil `requestId`, or `fireAndForget: true`, records a message that is complete as soon as it's sent.

## Connect

1. Run the app in a Simulator.
2. Open the Dari desktop app on the same Mac. The app appears under the **iOS Simulator** device.

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
