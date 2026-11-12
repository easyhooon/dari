@testable import Dari
@testable import DariUI
import XCTest

@MainActor
final class InspectorModelTests: XCTestCase {
    private var clock: Int64 = 0

    func testEmptyBeforeDariStarts() {
        let model = InspectorModel(source: { nil })
        model.start()
        XCTAssertFalse(model.isRunning)
        XCTAssertEqual(model.records, [])
    }

    func testListsNewestFirstAndFiltersByQuery() {
        let store = MessageStore(maxEntries: 10)
        add(store, "getUser", data: #"{"id":7}"#)
        add(store, "openCamera", data: nil)
        let model = InspectorModel(source: { store })
        model.start()
        XCTAssertEqual(model.records.map(\.handlerName), ["openCamera", "getUser"])
        model.query = " \"id\":7 "
        XCTAssertEqual(model.visible.map(\.handlerName), ["getUser"])
    }

    func testReloadsAfterEventsIncludingSilentPrunes() async throws {
        let store = MessageStore(maxEntries: 10, retentionPeriod: 1) { [unowned self] in clock }
        add(store, "old", data: nil)
        let model = InspectorModel(source: { store }, coalesce: 0)
        model.start()
        XCTAssertEqual(model.records.map(\.handlerName), ["old"])

        // `old` is pruned without an event; the `added` event for `new` must still drop it from the list.
        clock = 5_000
        add(store, "new", data: nil)
        try await waitUntil { model.records.map(\.handlerName) == ["new"] }

        model.stop()
        add(store, "afterStop", data: nil)
        try await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(model.records.map(\.handlerName), ["new"])
    }

    private func add(_ store: MessageStore, _ handler: String, data: String?) {
        store.add { id, now in
            MessageRecord(id: id, requestId: nil, handlerName: handler, direction: .webToApp, requestData: data, status: .success, requestTimestamp: now)
        }
    }

    private func waitUntil(_ condition: () -> Bool) async throws {
        for _ in 0..<100 where !condition() {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertTrue(condition())
    }
}
