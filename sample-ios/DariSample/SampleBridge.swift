import Dari
import Foundation
import WebKit

/// A typical callback-id bridge: the page posts `{type, handler, requestId, data}` and the app answers
/// through `onBridgeResponse`. Each Dari call sits next to the bridge code it records.
final class SampleBridge: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
    static let name = "bridge"

    weak var webView: WKWebView?
    private let dari = Dari.shared.interceptor(tag: "sample")
    private var appToWebCount = 0

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let type = body["type"] as? String else { return }
        let handler = body["handler"] as? String ?? ""
        let requestId = body["requestId"] as? String
        let data = body["data"] as? String
        switch type {
        case "request":
            dari.onWebToAppRequest(handlerName: handler, requestId: requestId, requestData: data)
            if let requestId { handle(handler, requestId: requestId, data: data) }
        case "fireAndForget":
            dari.onWebToAppRequest(handlerName: handler, requestId: nil, requestData: data, fireAndForget: true)
        case "appToWebResponse":
            dari.onAppToWebResponse(requestId: requestId, isSuccess: body["success"] as? Bool ?? false, responseData: data)
        case "askAppToCallWeb":
            callWeb(handler)
        default:
            break
        }
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        dari.onPageUrlChanged(webView.url?.absoluteString)
    }

    private func handle(_ handler: String, requestId: String, data: String?) {
        switch handler {
        case "getAppInfo":
            let info = Bundle.main.infoDictionary ?? [:]
            respond(handler, requestId, success: true, json([
                "bundleId": Bundle.main.bundleIdentifier ?? "",
                "version": info["CFBundleShortVersionString"] as? String ?? "",
                "platform": "ios",
            ]))
        case "showToast", "hapticFeedback":
            respond(handler, requestId, success: true, json(["shown": true]))
        case "signIn":
            respond(handler, requestId, success: true, json(["accessToken": "eyJhbGciOiJIUzI1NiJ9.sample", "userId": 42]))
        case "simulateSlowResponse":
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                self.respond(handler, requestId, success: true, self.json(["waitedSeconds": 3]))
            }
        case "simulateError":
            respond(handler, requestId, success: false, json(["error": "network_timeout"]))
        case "noResponsePending":
            break // Stays IN_PROGRESS in Dari.
        default:
            respond(handler, requestId, success: false, json(["error": "Unsupported handler", "handler": handler]))
        }
    }

    private func respond(_ handler: String, _ requestId: String, success: Bool, _ data: String) {
        dari.onWebToAppResponse(handlerName: handler, requestId: requestId, responseData: data, isSuccess: success)
        evaluate("onBridgeResponse(\(literal(requestId)), \(success), \(literal(data)))")
    }

    // App → Web: the page answers through a `appToWebResponse` message.
    private func callWeb(_ handler: String) {
        appToWebCount += 1
        let requestId = "app_\(appToWebCount)"
        let data = json(["source": "ios", "sentAt": Int(Date().timeIntervalSince1970)])
        dari.onAppToWebRequest(handlerName: handler, requestId: requestId, data: data)
        evaluate("onAppRequest(\(literal(handler)), \(literal(requestId)), \(literal(data)))")
    }

    private func evaluate(_ script: String) {
        webView?.evaluateJavaScript(script)
    }

    private func json(_ object: [String: Any]) -> String {
        let data = (try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])) ?? Data("{}".utf8)
        return String(decoding: data, as: UTF8.self)
    }

    /// A JavaScript string literal for [text].
    private func literal(_ text: String) -> String {
        let data = (try? JSONEncoder().encode(text)) ?? Data("\"\"".utf8)
        return String(decoding: data, as: UTF8.self)
    }
}
