@testable import Dari
@testable import DariUI
import XCTest

final class DeliveryFormatTests: XCTestCase {
    func testPendingTurnsIntoNotConfirmedAfterTheGracePeriod() {
        var record = MessageRecord(
            id: 1, requestId: "r", handlerName: "h", direction: .webToApp, status: .success, requestTimestamp: 1_000
        )
        XCTAssertNil(InspectorFormat.delivery(record, now: 99_000))
        record.delivery = .pending
        XCTAssertEqual(InspectorFormat.delivery(record, now: 1_000 + InspectorFormat.deliveryGraceMillis - 1), "Pending")
        XCTAssertEqual(
            InspectorFormat.delivery(record, now: 1_000 + InspectorFormat.deliveryGraceMillis),
            "Not confirmed: the page never ran it"
        )
        record.delivery = .delivered
        XCTAssertEqual(InspectorFormat.delivery(record, now: 0), "Delivered to the page")
    }
}
