@testable import Dari
import XCTest

/// Fixtures come from the Kotlin serializers (IosWireFixturesTest), so these pin the Swift side to them.
final class WireFixturesTests: XCTestCase {
    private let summary = WireMessageSummary(
        id: 3, requestId: "1", handlerName: "getUser", displayName: "Get user", direction: "WEB_TO_APP", tag: "demo",
        status: "SUCCESS", requestTimestamp: 1_700_000_000_000, responseTimestamp: 1_700_000_000_250,
        requestSizeBytes: 8, responseSizeBytes: 15, pageUrl: "https://example.com/checkout"
    )

    func testDecodesDesktopFrames() throws {
        XCTAssertEqual(
            try JSONDecoder().decode(ClientHello.self, from: fixture("client_hello")),
            ClientHello(protocolVersion: 1, clientName: "dari-desktop", clientVersion: "3.0.0")
        )
        guard case let .invoke(requestId, op, input) = try JSONDecoder().decode(IncomingFrame.self, from: fixture("invoke_detail")) else {
            return XCTFail("expected invoke")
        }
        XCTAssertEqual(requestId, "7")
        XCTAssertEqual(op, InspectorOperation.detail)
        XCTAssertEqual(try input.decode(DetailInput.self).id, 3)
        guard case let .invoke(_, observeOp, observeInput) = try JSONDecoder().decode(IncomingFrame.self, from: fixture("invoke_observe")) else {
            return XCTFail("expected invoke")
        }
        XCTAssertEqual(observeOp, InspectorOperation.observe)
        XCTAssertEqual(observeInput, .object([:]))
        guard case let .cancel(cancelled) = try JSONDecoder().decode(IncomingFrame.self, from: fixture("cancel")) else {
            return XCTFail("expected cancel")
        }
        XCTAssertEqual(cancelled, "8")
    }

    func testEncodesAppFramesLikeKotlin() throws {
        let app = AppInfo(
            applicationId: "com.example.app", appName: "Example", appVersion: "1.0", dariVersion: "3.0.0-alpha01",
            deviceModel: "iPhone17,1 (Simulator)", sdkInt: 0, environment: ["server": "demo"], platform: "ios", osVersion: "18.2.0"
        )
        try assertMatches("server_hello", ServerHello(accepted: true, protocolVersion: 1, app: app))
        try assertMatches("result_list", ResultFrame(requestId: "1", output: ListOutput(messages: [summary])))
        try assertMatches("result_detail", ResultFrame(requestId: "2", output: WireMessageDetail(
            summary: summary, requestData: #"{"id":7}"#, responseData: #"{"name":"Dari"}"#
        )))
        try assertMatches("result_observe_added", ResultFrame(requestId: "3", output: ObserveEvent(type: "added", message: summary), isFinal: false))
        try assertMatches("result_observe_cleared", ResultFrame(requestId: "3", output: ObserveEvent(type: "cleared"), isFinal: false))
        try assertMatches("result_clear", ResultFrame(requestId: "4", output: EmptyObject()))
        try assertMatches("result_error", ResultFrame<EmptyObject>(
            requestId: "5", error: InspectorError(code: InspectorError.operationNotFound, message: "Unknown op x")
        ))
        try assertMatches("result_search", ResultFrame(requestId: "6", output: SearchOutput(ids: [3])))
    }

    private func assertMatches<T: Encodable>(_ name: String, _ value: T, file: StaticString = #filePath, line: UInt = #line) throws {
        let expected = try JSONSerialization.jsonObject(with: fixture(name)) as? NSDictionary
        let actual = try JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) as? NSDictionary
        XCTAssertEqual(actual, expected, "\(name).json", file: file, line: line)
    }

    private func fixture(_ name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures"))
        return try Data(contentsOf: url)
    }
}
