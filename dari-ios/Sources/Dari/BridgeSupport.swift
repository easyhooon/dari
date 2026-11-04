import WebKit

/// Called from `@DariBridge` expansions in the `DariMacros` product. Not meant for direct use.
public enum _DariBridgeSupport {
    public static func record(_ message: WKScriptMessage, tag: String? = nil, parseMessage: ((String, Any) -> DariCapture)? = nil) {
        guard !WebKitHooks.recorded else { return }
        _ = Dari.shared.captureMessage(message, replying: false, tag: tag, parse: parseMessage ?? DariAutoCapture.defaultMessage)
    }

    /// Records [message] and returns a reply handler that records the reply too.
    public static func record(
        _ message: WKScriptMessage,
        tag: String? = nil,
        parseMessage: ((String, Any) -> DariCapture)? = nil,
        replyHandler: @escaping (Any?, String?) -> Void
    ) -> (Any?, String?) -> Void {
        guard !WebKitHooks.recorded,
              let captured = Dari.shared.captureMessage(message, replying: true, tag: tag, parse: parseMessage ?? DariAutoCapture.defaultMessage) else {
            return replyHandler
        }
        return recordingReply(captured, replyHandler)
    }
}
