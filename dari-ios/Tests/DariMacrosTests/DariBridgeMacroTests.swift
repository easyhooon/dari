import DariMacrosPlugin
import SwiftSyntaxMacros
import SwiftSyntaxMacrosTestSupport
import XCTest

final class DariBridgeMacroTests: XCTestCase {
    private let macros: [String: Macro.Type] = ["DariBridge": DariBridgeMacro.self, "DariBridgeHandler": DariBridgeHandlerMacro.self]

    func testMarksOnlyMessageHandlers() {
        assertMacroExpansion(
            """
            @DariBridge(tag: "web")
            final class Bridge {
                func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {}
                func helper() {}
            }
            """,
            expandedSource: """
            final class Bridge {
                @DariBridgeHandler(tag: "web")
                func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {}
                func helper() {}
            }
            """,
            macros: ["DariBridge": DariBridgeMacro.self]
        )
    }

    func testRecordsMessageBeforeTheBody() {
        assertMacroExpansion(
            """
            @DariBridgeHandler(tag: "web")
            func userContentController(_ controller: WKUserContentController, didReceive msg: WKScriptMessage) {
                handle(msg)
            }
            """,
            expandedSource: """
            func userContentController(_ controller: WKUserContentController, didReceive msg: WKScriptMessage) {
                _DariBridgeSupport.record(msg, tag: "web")
                handle(msg)
            }
            """,
            macros: macros
        )
    }

    func testWrapsTheReplyHandler() {
        assertMacroExpansion(
            """
            @DariBridgeHandler
            func userContentController(_ c: WKUserContentController, didReceive message: WKScriptMessage, replyHandler reply: @escaping (Any?, String?) -> Void) {
                reply(1, nil)
            }
            """,
            expandedSource: """
            func userContentController(_ c: WKUserContentController, didReceive message: WKScriptMessage, replyHandler reply: @escaping (Any?, String?) -> Void) {
                let reply = _DariBridgeSupport.record(message, replyHandler: reply)
                reply(1, nil)
            }
            """,
            macros: macros
        )
    }

    func testRejectsOtherFunctions() {
        assertMacroExpansion(
            """
            @DariBridgeHandler
            func load() {}
            """,
            expandedSource: """
            func load() {}
            """,
            diagnostics: [
                DiagnosticSpec(
                    message: "@DariBridgeHandler goes on userContentController(_:didReceive:) or userContentController(_:didReceive:replyHandler:)",
                    line: 1,
                    column: 1
                ),
            ],
            macros: macros
        )
    }
}
