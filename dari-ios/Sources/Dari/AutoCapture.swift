import Foundation
import ObjectiveC
import WebKit

/// What one bridge message or script means to Dari. Returned by the [DariAutoCapture] parsers.
public enum DariCapture {
    /// A new call: Web → App for a message, App → Web for a script.
    case request(handlerName: String, requestId: String?, data: String?, fireAndForget: Bool? = nil)
    /// An answer to an earlier call: the page answering the app (message) or the app answering the page (script).
    case response(requestId: String, data: String?, isSuccess: Bool)
    case ignore
}

/// Records WebKit bridge traffic without manual [DariInterceptor] calls, like the Android Gradle plugin.
///
/// Handlers added before [Dari.start] aren't wrapped, so start Dari before creating web views.
public struct DariAutoCapture {
    public var tag: String?
    /// Maps a `WKScriptMessage` (handler name and body) to a capture.
    public var parseMessage: (_ name: String, _ body: Any) -> DariCapture
    /// Maps a script passed to `evaluateJavaScript` to a capture.
    public var parseScript: (_ script: String) -> DariCapture

    public init(
        tag: String? = nil,
        parseMessage: @escaping (_ name: String, _ body: Any) -> DariCapture = DariAutoCapture.defaultMessage,
        parseScript: @escaping (_ script: String) -> DariCapture = DariAutoCapture.defaultScript
    ) {
        self.tag = tag
        self.parseMessage = parseMessage
        self.parseScript = parseScript
    }

    /// Reads `handler`, `requestId` and `data` from a dictionary body, falling back to the handler name and whole body.
    public static func defaultMessage(name: String, body: Any) -> DariCapture {
        guard let fields = body as? [String: Any] else {
            return .request(handlerName: name, requestId: nil, data: stringify(body))
        }
        return .request(
            handlerName: fields["handler"] as? String ?? name,
            requestId: fields["requestId"].flatMap(stringify),
            data: stringify(fields["data"] ?? body)
        )
    }

    /// Names the call after the function it starts with, e.g. `window.app.onPush` for `window.app.onPush({...})`.
    public static func defaultScript(_ script: String) -> DariCapture {
        .request(handlerName: functionName(script) ?? "evaluateJavaScript", requestId: nil, data: script)
    }

    /// Strings as they are, everything else as JSON.
    public static func stringify(_ value: Any?) -> String? {
        switch value {
        case nil, is NSNull: return nil
        case let text as String: return text
        case let value?:
            guard let data = try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys, .fragmentsAllowed]) else {
                return String(describing: value)
            }
            return String(decoding: data, as: UTF8.self)
        }
    }

    static func functionName(_ script: String) -> String? {
        let trimmed = script.drop { $0.isWhitespace }
        let name = trimmed.prefix { $0.isLetter || $0.isNumber || $0 == "_" || $0 == "$" || $0 == "." }
        guard !name.isEmpty, trimmed.dropFirst(name.count).drop(while: \.isWhitespace).first == "(" else { return nil }
        return String(name)
    }
}

/// Ids for calls whose protocol has none, so replies and completions still pair.
enum AutoCaptureIds {
    private static let lock = NSLock()
    private static var counter = 0

    static func next() -> String {
        lock.withLock {
            counter += 1
            return "auto-\(counter)"
        }
    }
}

extension Dari {
    /// The settings and this web view's interceptor, or nil when auto capture is off.
    func autoCaptureContext(_ webView: WKWebView?) -> (DariAutoCapture, DariInterceptor)? {
        guard let capture = autoCapture else { return nil }
        guard let webView else { return (capture, interceptor(tag: capture.tag)) }
        let existing = objc_getAssociatedObject(webView, &WebKitHooks.interceptorKey) as? DariInterceptor
        let webViewInterceptor = existing ?? interceptor(tag: capture.tag)
        if existing == nil {
            objc_setAssociatedObject(webView, &WebKitHooks.interceptorKey, webViewInterceptor, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        }
        webViewInterceptor.onPageUrlChanged(webView.url?.absoluteString)
        return (capture, webViewInterceptor)
    }

    /// Records a Web → App message and returns what a reply needs to pair with it.
    /// Only [replying] handlers get a generated id, since nothing else answers an id-less message.
    func captureMessage(_ message: WKScriptMessage, replying: Bool) -> (DariInterceptor, handlerName: String, requestId: String)? {
        guard let (capture, interceptor) = autoCaptureContext(message.webView) else { return nil }
        switch capture.parseMessage(message.name, message.body) {
        case let .request(handlerName, requestId, data, fireAndForget):
            let requestId = requestId ?? (replying ? AutoCaptureIds.next() : nil)
            interceptor.onWebToAppRequest(handlerName: handlerName, requestId: requestId, requestData: data, fireAndForget: fireAndForget)
            return requestId.map { (interceptor, handlerName, $0) }
        case let .response(requestId, data, isSuccess):
            interceptor.onAppToWebResponse(requestId: requestId, isSuccess: isSuccess, responseData: data)
        case .ignore:
            break
        }
        return nil
    }

    /// Records an App → Web script. Returns a completion that records the result when the script itself is the call.
    func captureScript(_ script: String, webView: WKWebView) -> ((Any?, Error?) -> Void)? {
        guard let (capture, interceptor) = autoCaptureContext(webView) else { return nil }
        switch capture.parseScript(script) {
        case let .request(handlerName, requestId?, data, fireAndForget):
            // The page answers this one through a message.
            interceptor.onAppToWebRequest(handlerName: handlerName, requestId: requestId, data: data, fireAndForget: fireAndForget)
        case let .request(handlerName, nil, data, fireAndForget):
            let requestId = AutoCaptureIds.next()
            interceptor.onAppToWebRequest(handlerName: handlerName, requestId: requestId, data: data, fireAndForget: fireAndForget)
            return { result, error in
                interceptor.onAppToWebResponse(
                    requestId: requestId,
                    isSuccess: error == nil,
                    responseData: error.map { $0.localizedDescription } ?? DariAutoCapture.stringify(result)
                )
            }
        case let .response(requestId, data, isSuccess):
            interceptor.onWebToAppResponse(handlerName: "", requestId: requestId, responseData: data, isSuccess: isSuccess)
        case .ignore:
            break
        }
        return nil
    }
}

final class RecordingMessageHandler: NSObject, WKScriptMessageHandler {
    let wrapped: WKScriptMessageHandler

    init(_ wrapped: WKScriptMessageHandler) {
        self.wrapped = wrapped
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        _ = Dari.shared.captureMessage(message, replying: false)
        wrapped.userContentController(controller, didReceive: message)
    }
}

final class RecordingReplyHandler: NSObject, WKScriptMessageHandlerWithReply {
    let wrapped: WKScriptMessageHandlerWithReply

    init(_ wrapped: WKScriptMessageHandlerWithReply) {
        self.wrapped = wrapped
    }

    func userContentController(
        _ controller: WKUserContentController,
        didReceive message: WKScriptMessage,
        replyHandler: @escaping (Any?, String?) -> Void
    ) {
        guard let (interceptor, handlerName, requestId) = Dari.shared.captureMessage(message, replying: true) else {
            return wrapped.userContentController(controller, didReceive: message, replyHandler: replyHandler)
        }
        wrapped.userContentController(controller, didReceive: message) { reply, error in
            interceptor.onWebToAppResponse(
                handlerName: handlerName,
                requestId: requestId,
                responseData: error ?? DariAutoCapture.stringify(reply),
                isSuccess: error == nil
            )
            replyHandler(reply, error)
        }
    }
}

/// Runtime hooks on WebKit's bridge entry points. Installed once and inert while auto capture is off.
enum WebKitHooks {
    static var interceptorKey: UInt8 = 0
    /// WebKit may route one evaluate overload through another; record only the outermost call.
    static var evaluating = false
    private static let installOnce: Void = {
        swizzle(WKUserContentController.self, "addScriptMessageHandler:name:", #selector(WKUserContentController.dari_add(_:name:)))
        swizzle(
            WKUserContentController.self,
            "addScriptMessageHandler:contentWorld:name:",
            #selector(WKUserContentController.dari_add(_:contentWorld:name:))
        )
        swizzle(
            WKUserContentController.self,
            "addScriptMessageHandlerWithReply:contentWorld:name:",
            #selector(WKUserContentController.dari_addWithReply(_:contentWorld:name:))
        )
        swizzle(WKWebView.self, "evaluateJavaScript:completionHandler:", #selector(WKWebView.dari_evaluateJavaScript(_:completionHandler:)))
        swizzle(
            WKWebView.self,
            "evaluateJavaScript:inFrame:inContentWorld:completionHandler:",
            #selector(WKWebView.dari_evaluateJavaScript(_:inFrame:inContentWorld:completionHandler:))
        )
    }()

    static func install() {
        _ = installOnce
    }

    private static func swizzle(_ cls: AnyClass, _ original: String, _ replacement: Selector) {
        guard let originalMethod = class_getInstanceMethod(cls, NSSelectorFromString(original)),
              let replacementMethod = class_getInstanceMethod(cls, replacement) else { return }
        method_exchangeImplementations(originalMethod, replacementMethod)
    }

    static func wrap(_ handler: WKScriptMessageHandler) -> WKScriptMessageHandler {
        guard Dari.shared.autoCapture != nil, !(handler is RecordingMessageHandler) else { return handler }
        return RecordingMessageHandler(handler)
    }

    static func wrap(_ handler: WKScriptMessageHandlerWithReply) -> WKScriptMessageHandlerWithReply {
        guard Dari.shared.autoCapture != nil, !(handler is RecordingReplyHandler) else { return handler }
        return RecordingReplyHandler(handler)
    }

    /// Records [script], then runs WebKit's original with a completion that also records the result when needed.
    static func evaluate(
        _ script: String,
        on webView: WKWebView,
        _ completionHandler: ((Any?, Error?) -> Void)?,
        original: (((Any?, Error?) -> Void)?) -> Void
    ) {
        guard !evaluating, let record = Dari.shared.captureScript(script, webView: webView) else {
            let outermost = !evaluating
            evaluating = true
            defer { if outermost { evaluating = false } }
            return original(completionHandler)
        }
        evaluating = true
        defer { evaluating = false }
        original { result, error in
            record(result, error)
            completionHandler?(result, error)
        }
    }
}

// After swizzling, each `dari_` method calls WebKit's original implementation.
extension WKUserContentController {
    @objc dynamic func dari_add(_ handler: WKScriptMessageHandler, name: String) {
        dari_add(WebKitHooks.wrap(handler), name: name)
    }

    @objc dynamic func dari_add(_ handler: WKScriptMessageHandler, contentWorld: WKContentWorld, name: String) {
        dari_add(WebKitHooks.wrap(handler), contentWorld: contentWorld, name: name)
    }

    @objc dynamic func dari_addWithReply(_ handler: WKScriptMessageHandlerWithReply, contentWorld: WKContentWorld, name: String) {
        dari_addWithReply(WebKitHooks.wrap(handler), contentWorld: contentWorld, name: name)
    }
}

extension WKWebView {
    @objc dynamic func dari_evaluateJavaScript(_ script: String, completionHandler: ((Any?, Error?) -> Void)?) {
        WebKitHooks.evaluate(script, on: self, completionHandler) { self.dari_evaluateJavaScript(script, completionHandler: $0) }
    }

    // Same block type as WebKit's ObjC method; Swift shows it as a Result-based overload.
    @objc dynamic func dari_evaluateJavaScript(
        _ script: String,
        inFrame frame: WKFrameInfo?,
        inContentWorld contentWorld: WKContentWorld,
        completionHandler: ((Any?, Error?) -> Void)?
    ) {
        WebKitHooks.evaluate(script, on: self, completionHandler) {
            self.dari_evaluateJavaScript(script, inFrame: frame, inContentWorld: contentWorld, completionHandler: $0)
        }
    }
}
