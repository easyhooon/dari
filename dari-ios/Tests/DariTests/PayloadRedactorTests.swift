@testable import Dari
import XCTest

final class PayloadRedactorTests: XCTestCase {
    private struct Failure: Error {}

    private let record = MessageRecord(
        id: 7, requestId: "r", handlerName: "h", direction: .appToWeb, tag: "main",
        requestData: "req", responseData: "res", status: .success, requestTimestamp: 1, pageUrl: "https://a.b/c"
    )

    func testAppliesEditablePayloadsAndPageUrl() {
        let redacted = record.redacted { message in
            XCTAssertEqual(message.direction, .appToWeb)
            XCTAssertEqual(message.tag, "main")
            var message = message
            message.requestData = "x"
            message.responseData = "y"
            message.pageUrl = nil
            return message
        }
        XCTAssertEqual(redacted.requestData, "x")
        XCTAssertEqual(redacted.responseData, "y")
        XCTAssertNil(redacted.pageUrl)
        XCTAssertEqual(redacted.id, 7)
    }

    func testThrowingRedactorHidesPayloads() {
        let redacted = record.redacted { _ in throw Failure() }
        XCTAssertEqual(redacted.requestData, MessageRecord.redactorFailed)
        XCTAssertEqual(redacted.responseData, MessageRecord.redactorFailed)
        XCTAssertNil(redacted.pageUrl)
    }

    func testThrowingRedactorKeepsMissingPayloadsNil() {
        var empty = record
        empty.requestData = nil
        empty.responseData = nil
        let redacted = empty.redacted { _ in throw Failure() }
        XCTAssertNil(redacted.requestData)
        XCTAssertNil(redacted.responseData)
    }

    func testAppsCanBuildMessagesForTheirOwnTests() {
        let message = DariMessage(id: 1, requestId: nil, handlerName: "h", direction: .webToApp, requestData: "x")
        XCTAssertEqual(message.requestData, "x")
    }

    func testDariAppliesRedactorOnlyWhenSet() {
        let dari = Dari()
        XCTAssertEqual(dari.redact(record), record)
        dari.setPayloadRedactor { message in
            var message = message
            message.requestData = "x"
            return message
        }
        XCTAssertEqual(dari.redact(record).requestData, "x")
        dari.setPayloadRedactor(nil)
        XCTAssertEqual(dari.redact(record), record)
    }
}
