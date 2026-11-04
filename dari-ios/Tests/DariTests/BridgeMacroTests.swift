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
        XCTAssertEqual(records.map(\.handlerName), ["toast", "getUser", "getToken", "Math.max", "Math.min", "Math.abs"])
        XCTAssertEqual(Set(records.map(\.tag)), ["macro"])
        XCTAssertEqual(records[1].responseData, #"{"name":"dari"}"#)
        XCTAssertEqual(records[1].status, .success)
        XCTAssertEqual(records[2].responseData, "token expired")
        XCTAssertEqual(records[2].status, .error)
        XCTAssertEqual(records[3].responseData, "2")
        XCTAssertEqual(records[4].responseData, "1")
        XCTAssertEqual(records[5].responseData, "3")
    }

    @MainActor
    func testAutoCaptureRecordsOnce() throws {
        try Dari.shared.start(DariConfiguration(ports: 38_700...38_707, autoCapture: DariAutoCapture(tag: "auto")))
        try runPage()
        XCTAssertEqual(Dari.shared.records.map(\.handlerName), ["toast", "getUser", "getToken", "Math.max", "Math.min", "Math.abs"])
        XCTAssertEqual(Dari.shared.records.map(\.tag), ["auto", "auto", "auto", "macro", "macro", "macro"])
    }

    @MainActor
    private func runPage() throws {
        let handler = MacroHandler()
        let configuration = WKWebViewConfiguration()
        configuration.userContentController.add(handler, name: "bridge")
        configuration.userContentController.addScriptMessageHandler(handler, contentWorld: .page, name: "ask")
        configuration.userContentController.addScriptMessageHandler(AsyncHandler(handler), contentWorld: .page, name: "later")
        let webView = WKWebView(frame: .zero, configuration: configuration)
        handler.expectation = expectation(description: "messages")
        handler.expectation?.expectedFulfillmentCount = 3
        webView.loadHTMLString(
            """
            <script>
            webkit.messageHandlers.bridge.postMessage({handler: "toast", data: "hello"});
            webkit.messageHandlers.ask.postMessage({handler: "getUser"});
            webkit.messageHandlers.later.postMessage({handler: "getToken"});
            </script>
            """,
            baseURL: URL(string: "https://example.com")
        )
        wait(for: [handler.expectation!], timeout: 10)

        let evaluated = expectation(description: "script")
        webView.dariEvaluateJavaScript("Math.max(1, 2)", tag: "macro") { _, _ in evaluated.fulfill() }
        wait(for: [evaluated], timeout: 10)

        let called = expectation(description: "async scripts")
        Task {
            _ = try await webView.dariEvaluateJavaScript("Math.min(1, 2)", contentWorld: .page, tag: "macro")
            _ = try await webView.dariCallAsyncJavaScript("return Math.abs(x)", arguments: ["x": -3], contentWorld: .page, tag: "macro")
            called.fulfill()
        }
        wait(for: [called], timeout: 10)
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

private final class AsyncHandler: NSObject {
    let main: MacroHandler
    init(_ main: MacroHandler) { self.main = main }
}

@DariBridge(tag: "macro")
extension AsyncHandler: WKScriptMessageHandlerWithReply {
    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) async -> (Any?, String?) {
        await Task.yield()
        main.expectation?.fulfill()
        return (nil, "token expired")
    }
}
