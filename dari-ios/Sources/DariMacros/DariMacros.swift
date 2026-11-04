@_exported import Dari

/// Records every `WKScriptMessageHandler` method of the type at compile time, without swizzling.
/// Arguments match `DariAutoCapture`: [tag] labels the records, [parseMessage] reads the message body.
@attached(memberAttribute)
public macro DariBridge(tag: String? = nil, parseMessage: ((String, Any) -> DariCapture)? = nil) =
    #externalMacro(module: "DariMacrosPlugin", type: "DariBridgeMacro")

/// Records one `userContentController(_:didReceive:)` or `userContentController(_:didReceive:replyHandler:)`.
@attached(body)
public macro DariBridgeHandler(tag: String? = nil, parseMessage: ((String, Any) -> DariCapture)? = nil) =
    #externalMacro(module: "DariMacrosPlugin", type: "DariBridgeHandlerMacro")
