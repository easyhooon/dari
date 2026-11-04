@testable import Dari
import WebKit
import XCTest

final class AutoCaptureTests: XCTestCase {
    override func tearDown() {
        Dari.shared.stop()
        super.tearDown()
    }

    func testDefaultMessageReadsBridgeFields() {
        let capture = DariAutoCapture.defaultMessage(name: "bridge", body: ["handler": "getUser", "requestId": 7, "data": ["id": 1]])
        guard case let .request(handlerName, requestId, data, _) = capture else { return XCTFail("\(capture)") }
        XCTAssertEqual(handlerName, "getUser")
        XCTAssertEqual(requestId, "7")
        XCTAssertEqual(data, #"{"id":1}"#)
    }

    func testDefaultMessageFallsBackToHandlerNameAndBody() {
        guard case let .request(handlerName, requestId, data, _) = DariAutoCapture.defaultMessage(name: "log", body: "hi") else {
            return XCTFail()
        }
        XCTAssertEqual(handlerName, "log")
        XCTAssertNil(requestId)
        XCTAssertEqual(data, "hi")
    }

    func testDefaultScriptNamesTheCalledFunction() {
        XCTAssertEqual(DariAutoCapture.functionName("  window.app.onPush ({})"), "window.app.onPush")
        XCTAssertNil(DariAutoCapture.functionName("document.title"))
        guard case let .request(handlerName, _, _, _) = DariAutoCapture.defaultScript("1 + 1") else { return XCTFail() }
        XCTAssertEqual(handlerName, "evaluateJavaScript")
    }

    @MainActor
    func testRecordsMessagesRepliesAndScripts() throws {
        try Dari.shared.start(DariConfiguration(ports: 38_690...38_697, autoCapture: DariAutoCapture(tag: "auto")))
        let handler = Handler()
        let configuration = WKWebViewConfiguration()
        configuration.userContentController.add(handler, name: "bridge")
        configuration.userContentController.addScriptMessageHandler(handler, contentWorld: .page, name: "ask")
        let webView = WKWebView(frame: .zero, configuration: configuration)

        handler.expectation = expectation(description: "messages")
        handler.expectation?.expectedFulfillmentCount = 3
        webView.loadHTMLString(
            """
            <script>
            webkit.messageHandlers.bridge.postMessage({handler: "toast", requestId: "r1", data: "hello"});
            webkit.messageHandlers.bridge.postMessage("log");
            webkit.messageHandlers.ask.postMessage({handler: "getUser"});
            </script>
            """,
            baseURL: URL(string: "https://example.com/page?secret=1")
        )
        wait(for: [handler.expectation!], timeout: 10)

        let evaluated = expectation(description: "script")
        webView.evaluateJavaScript("Math.max(1, 2)") { _, _ in evaluated.fulfill() }
        wait(for: [evaluated], timeout: 10)

        let records = Dari.shared.records
        XCTAssertEqual(records.map(\.handlerName), ["toast", "bridge", "getUser", "Math.max"])
        XCTAssertEqual(records.map(\.direction), [.webToApp, .webToApp, .webToApp, .appToWeb])
        XCTAssertEqual(records.map(\.tag), ["auto", "auto", "auto", "auto"])
        XCTAssertEqual(records[0].requestId, "r1")
        XCTAssertEqual(records[0].status, .inProgress)
        XCTAssertEqual(records[0].pageUrl, "https://example.com/page")
        XCTAssertNil(records[1].requestId)
        XCTAssertNotNil(records[2].requestId)
        XCTAssertEqual(records[2].responseData, #"{"name":"dari"}"#)
        XCTAssertEqual(records[2].status, .success)
        XCTAssertEqual(records[3].responseData, "2")
        XCTAssertEqual(records[3].status, .success)
    }
}

private final class Handler: NSObject, WKScriptMessageHandler, WKScriptMessageHandlerWithReply {
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
