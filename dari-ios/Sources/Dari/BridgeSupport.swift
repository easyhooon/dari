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

// Hookless versions of WebKit's script calls, for apps that use `@DariBridge` instead of `autoCapture`.
// [tag] and [parseScript] work like `DariAutoCapture`'s; `callAsyncJavaScript` passes its function body to [parseScript].
extension WKWebView {
    public func dariEvaluateJavaScript(
        _ script: String,
        tag: String? = nil,
        parseScript: ((String) -> DariCapture)? = nil,
        completionHandler: ((Any?, Error?) -> Void)? = nil
    ) {
        recordScript(script, tag: tag, parseScript: parseScript) { record in
            evaluateJavaScript(script) { result, error in
                record(result, error)
                completionHandler?(result, error)
            }
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

    public func dariEvaluateJavaScript(
        _ script: String,
        in frame: WKFrameInfo? = nil,
        in contentWorld: WKContentWorld,
        tag: String? = nil,
        parseScript: ((String) -> DariCapture)? = nil,
        completionHandler: ((Result<Any, Error>) -> Void)? = nil
    ) {
        recordScript(script, tag: tag, parseScript: parseScript) { record in
            evaluateJavaScript(script, in: frame, in: contentWorld) { result in
                record(result.value, result.error)
                completionHandler?(result)
            }
        }
    }

    @discardableResult
    public func dariEvaluateJavaScript(
        _ script: String,
        in frame: WKFrameInfo? = nil,
        contentWorld: WKContentWorld,
        tag: String? = nil,
        parseScript: ((String) -> DariCapture)? = nil
    ) async throws -> Any? {
        try await withCheckedThrowingContinuation { continuation in
            dariEvaluateJavaScript(script, in: frame, in: contentWorld, tag: tag, parseScript: parseScript) { continuation.resume(with: $0.map { value -> Any? in value }) }
        }
    }

    public func dariCallAsyncJavaScript(
        _ functionBody: String,
        arguments: [String: Any] = [:],
        in frame: WKFrameInfo? = nil,
        in contentWorld: WKContentWorld,
        tag: String? = nil,
        parseScript: ((String) -> DariCapture)? = nil,
        completionHandler: ((Result<Any, Error>) -> Void)? = nil
    ) {
        recordScript(functionBody, tag: tag, parseScript: parseScript ?? Self.defaultFunctionBody) { record in
            callAsyncJavaScript(functionBody, arguments: arguments, in: frame, in: contentWorld) { result in
                record(result.value, result.error)
                completionHandler?(result)
            }
        }
    }

    @discardableResult
    public func dariCallAsyncJavaScript(
        _ functionBody: String,
        arguments: [String: Any] = [:],
        in frame: WKFrameInfo? = nil,
        contentWorld: WKContentWorld,
        tag: String? = nil,
        parseScript: ((String) -> DariCapture)? = nil
    ) async throws -> Any? {
        try await withCheckedThrowingContinuation { continuation in
            dariCallAsyncJavaScript(functionBody, arguments: arguments, in: frame, in: contentWorld, tag: tag, parseScript: parseScript) {
                continuation.resume(with: $0.map { value -> Any? in value })
            }
        }
    }

    private static func defaultFunctionBody(_ body: String) -> DariCapture {
        DariAutoCapture.functionName(body) == nil
            ? .request(handlerName: "callAsyncJavaScript", requestId: nil, data: body)
            : DariAutoCapture.defaultScript(body)
    }

    /// Records [script], then lets [run] call WebKit with a recorder for its result.
    private func recordScript(
        _ script: String,
        tag: String?,
        parseScript: ((String) -> DariCapture)?,
        run: (_ record: @escaping (Any?, Error?) -> Void) -> Void
    ) {
        let record = Dari.shared.captureScript(script, webView: self, tag: tag, parse: parseScript ?? DariAutoCapture.defaultScript)
        // Keeps auto capture's hook, if installed, from recording the same call.
        let outer = WebKitHooks.evaluating
        WebKitHooks.evaluating = true
        defer { WebKitHooks.evaluating = outer }
        run { record?($0, $1) }
    }
}

private extension Result where Success == Any, Failure == Error {
    var value: Any? { try? get() }
    var error: Error? { if case let .failure(error) = self { error } else { nil } }
}
