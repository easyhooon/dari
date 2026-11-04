import WebKit

/// Called from `@DariBridge` expansions in the `DariMacros` product. Not meant for direct use.
public enum _DariBridgeSupport {
    public static func record(_ message: WKScriptMessage, tag: String? = nil, parseMessage: ((String, Any) -> DariCapture)? = nil) {
        guard !WebKitHooks.isRecorded(message) else { return }
        _ = Dari.shared.captureMessage(message, replying: false, tag: tag, parse: parseMessage ?? DariAutoCapture.defaultMessage)
    }

    /// Records [message] and returns a reply handler that records the reply too.
    public static func record(
        _ message: WKScriptMessage,
        tag: String? = nil,
        parseMessage: ((String, Any) -> DariCapture)? = nil,
        replyHandler: @escaping (Any?, String?) -> Void
    ) -> (Any?, String?) -> Void {
        guard !WebKitHooks.isRecorded(message),
              let captured = Dari.shared.captureMessage(message, replying: true, tag: tag, parse: parseMessage ?? DariAutoCapture.defaultMessage) else {
            return replyHandler
        }
        return recordingReply(captured, replyHandler)
    }

    /// Records [message], runs the async handler [body], and records what it returns as the reply.
    @MainActor
    public static func record(
        _ message: WKScriptMessage,
        tag: String? = nil,
        parseMessage: ((String, Any) -> DariCapture)? = nil,
        body: () async -> (Any?, String?)
    ) async -> (Any?, String?) {
        let reply = record(message, tag: tag, parseMessage: parseMessage) { _, _ in }
        let (value, error) = await body()
        reply(value, error)
        return (value, error)
    }
}

extension WKWebView {
    /// Runs [script] like `evaluateJavaScript` and records it, for apps that use `@DariBridge` instead of `autoCapture`.
    /// [tag] and [parseScript] work like `DariAutoCapture`'s.
    public func dariEvaluateJavaScript(
        _ script: String,
        tag: String? = nil,
        parseScript: ((String) -> DariCapture)? = nil,
        completionHandler: ((Any?, Error?) -> Void)? = nil
    ) {
        let record = Dari.shared.captureScript(script, webView: self, tag: tag, parse: parseScript ?? DariAutoCapture.defaultScript)
        // Keeps auto capture's hook, if installed, from recording the same call.
        let outer = WebKitHooks.evaluating
        WebKitHooks.evaluating = true
        defer { WebKitHooks.evaluating = outer }
        evaluateJavaScript(script) { result, error in
            record?(result, error)
            completionHandler?(result, error)
        }
    }

    @discardableResult
    public func dariEvaluateJavaScript(_ script: String, tag: String? = nil, parseScript: ((String) -> DariCapture)? = nil) async throws -> Any? {
        try await withCheckedThrowingContinuation { continuation in
            dariEvaluateJavaScript(script, tag: tag, parseScript: parseScript) { result, error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume(returning: result) }
            }
        }
    }
}
