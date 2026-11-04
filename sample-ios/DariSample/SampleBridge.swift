import DariMacros
import Foundation
import WebKit

/// A typical callback-id bridge: the page posts `{type, handler, requestId, data}` and the app answers
/// through `onBridgeResponse`. `@DariBridge` records the messages and `dariEvaluateJavaScript` the scripts,
/// so nothing is swizzled. Launch with `-DariAutoCapture YES` to record through [SampleCapture.autoCapture] instead.
@DariBridge(tag: "sample", parseMessage: SampleCapture.parseMessage)
final class SampleBridge: NSObject, WKScriptMessageHandler {
    static let name = "bridge"

    weak var webView: WKWebView?
    private var appToWebCount = 0

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let type = body["type"] as? String else { return }
        let handler = body["handler"] as? String ?? ""
        switch type {
        case "request":
            if let requestId = body["requestId"] as? String { handle(handler, requestId: requestId, data: body["data"] as? String) }
        case "askAppToCallWeb":
            callWeb(handler)
        default:
            break
        }
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
        evaluate("onBridgeResponse(\(literal(requestId)), \(success), \(literal(data)))")
    }

    // App → Web: the page answers through a `appToWebResponse` message.
    private func callWeb(_ handler: String) {
        appToWebCount += 1
        let requestId = "app_\(appToWebCount)"
        let data = json(["source": "ios", "sentAt": Int(Date().timeIntervalSince1970)])
        evaluate("onAppRequest(\(literal(handler)), \(literal(requestId)), \(literal(data)))")
    }

    private func evaluate(_ script: String) {
        webView?.dariEvaluateJavaScript(script, tag: "sample", parseScript: SampleCapture.parseScript)
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

/// Maps the sample bridge's messages and callback scripts to Dari captures. It lives outside SampleBridge
/// because a macro's arguments can't refer to the type the macro is attached to.
enum SampleCapture {
    /// Maps this bridge's message types to Dari captures.
    static let parseMessage: (String, Any) -> DariCapture = { name, body in
        guard let body = body as? [String: Any] else { return .ignore }
        let handler = body["handler"] as? String ?? name
        let data = body["data"] as? String
        switch body["type"] as? String {
        case "request":
            return .request(handlerName: handler, requestId: body["requestId"] as? String, data: data)
        case "fireAndForget":
            return .request(handlerName: handler, requestId: nil, data: data, fireAndForget: true)
        case "appToWebResponse":
            return .response(requestId: body["requestId"] as? String ?? "", data: data, isSuccess: body["success"] as? Bool ?? false)
        default:
            return .ignore
        }
    }

    /// Maps this bridge's callback scripts to Dari captures.
    static let parseScript: (String) -> DariCapture = { script in
        if let args = arguments(script, of: "onBridgeResponse"), let requestId = args.first as? String {
            return .response(requestId: requestId, data: args.last as? String, isSuccess: args.dropFirst().first as? Bool ?? false)
        }
        if let args = arguments(script, of: "onAppRequest"), let handler = args.first as? String {
            return .request(handlerName: handler, requestId: args.dropFirst().first as? String, data: args.last as? String)
        }
        return DariAutoCapture.defaultScript(script)
    }

    static let autoCapture = DariAutoCapture(tag: "sample", parseMessage: parseMessage, parseScript: parseScript)

    /// The arguments of a `function(...)` call whose arguments are JSON literals, as this bridge writes them.
    private static func arguments(_ script: String, of function: String) -> [Any]? {
        guard script.hasPrefix(function + "("), script.hasSuffix(")") else { return nil }
        let list = "[" + script.dropFirst(function.count + 1).dropLast() + "]"
        return (try? JSONSerialization.jsonObject(with: Data(list.utf8))) as? [Any]
    }
}
