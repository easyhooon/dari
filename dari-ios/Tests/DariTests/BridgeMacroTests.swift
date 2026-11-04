@testable import Dari
import DariMacros
import WebKit
import XCTest

final class BridgeMacroTests: XCTestCase {
    override func tearDown() {
        Dari.shared.stop()
        super.tearDown()
    }

    @MainActor
    func testRecordsWithoutAutoCapture() throws {
        try Dari.shared.start(DariConfiguration(ports: 38_700...38_707))
        try runPage()
        let records = Dari.shared.records
        XCTAssertEqual(records.map(\.handlerName), ["toast", "getUser"])
        XCTAssertEqual(records.map(\.tag), ["macro", "macro"])
        XCTAssertEqual(records[1].responseData, #"{"name":"dari"}"#)
        XCTAssertEqual(records[1].status, .success)
    }

    @MainActor
    func testAutoCaptureRecordsOnce() throws {
        try Dari.shared.start(DariConfiguration(ports: 38_700...38_707, autoCapture: DariAutoCapture(tag: "auto")))
        try runPage()
        XCTAssertEqual(Dari.shared.records.map(\.handlerName), ["toast", "getUser"])
        XCTAssertEqual(Dari.shared.records.map(\.tag), ["auto", "auto"])
    }

    @MainActor
    private func runPage() throws {
        let handler = MacroHandler()
        let configuration = WKWebViewConfiguration()
        configuration.userContentController.add(handler, name: "bridge")
        configuration.userContentController.addScriptMessageHandler(handler, contentWorld: .page, name: "ask")
        let webView = WKWebView(frame: .zero, configuration: configuration)
        handler.expectation = expectation(description: "messages")
        handler.expectation?.expectedFulfillmentCount = 2
        webView.loadHTMLString(
            """
            <script>
            webkit.messageHandlers.bridge.postMessage({handler: "toast", data: "hello"});
            webkit.messageHandlers.ask.postMessage({handler: "getUser"});
            </script>
            """,
            baseURL: URL(string: "https://example.com")
        )
        wait(for: [handler.expectation!], timeout: 10)
    }
}

@DariBridge(tag: "macro")
private final class MacroHandler: NSObject, WKScriptMessageHandler, WKScriptMessageHandlerWithReply {
    var expectation: XCTestExpectation?

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        expectation?.fulfill()
    }

    func userContentController(
        _ controller: WKUserContentController,
        didReceive message: WKScriptMessage,
        replyHandler: @escaping (Any?, String?) -> Void
    ) {
        replyHandler(["name": "dari"], nil)
        expectation?.fulfill()
    }
}
