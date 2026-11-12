@testable import Dari
@testable import DariUI
import XCTest

@MainActor
final class InspectorModelTests: XCTestCase {
    private var clock: Int64 = 0

    func testEmptyBeforeDariStarts() {
        let model = InspectorModel(source: { nil }, defaults: isolatedDefaults())
        model.start()
        XCTAssertFalse(model.isRunning)
        XCTAssertEqual(model.records, [])
    }

    func testListsNewestFirstAndFiltersByQuery() {
        let store = MessageStore(maxEntries: 10)
        add(store, "getUser", data: #"{"id":7}"#)
        add(store, "openCamera", data: nil)
        let model = InspectorModel(source: { store }, defaults: isolatedDefaults())
        model.start()
        XCTAssertEqual(model.records.map(\.handlerName), ["openCamera", "getUser"])
        model.query = " \"id\":7 "
        XCTAssertEqual(model.visible.map(\.handlerName), ["getUser"])
    }

    func testReloadsAfterEventsIncludingSilentPrunes() async throws {
        let store = MessageStore(maxEntries: 10, retentionPeriod: 1) { [unowned self] in clock }
        add(store, "old", data: nil)
        let model = InspectorModel(source: { store }, coalesce: 0, defaults: isolatedDefaults())
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

    func testFollowsDariStopAndStart() async throws {
        var current: MessageStore? = MessageStore(maxEntries: 10)
        add(current!, "old", data: nil)
        let model = InspectorModel(source: { current }, coalesce: 0, defaults: isolatedDefaults())
        model.start()
        XCTAssertEqual(model.records.map(\.handlerName), ["old"])

        current = nil
        NotificationCenter.default.post(name: Dari.storeDidChange, object: nil)
        try await waitUntil { !model.isRunning && model.records.isEmpty }

        let fresh = MessageStore(maxEntries: 10)
        add(fresh, "fresh", data: nil)
        current = fresh
        NotificationCenter.default.post(name: Dari.storeDidChange, object: nil)
        try await waitUntil { model.records.map(\.handlerName) == ["fresh"] }
    }

    func testMaskingSwitchMasksImmediatelyAndPersists() throws {
        let defaults = isolatedDefaults()
        let store = MessageStore(maxEntries: 10)
        add(store, "signIn", data: #"{"password":"abc","email":"a@b.c"}"#)
        let model = InspectorModel(source: { store }, coalesce: 0, defaults: defaults)
        model.start()
        let raw = try XCTUnwrap(model.records.first)
        XCTAssertEqual(model.display(raw).requestData, raw.requestData)

        // Synchronous: no window where the screens or search see raw values after switching on.
        model.maskingEnabled = true
        // PayloadMasker reorders JSON keys, so check the parts.
        let shown = try XCTUnwrap(model.display(raw).requestData)
        XCTAssertTrue(shown.contains(#""password":"***""#) && shown.contains("a@b.c") && !shown.contains("abc"))
        XCTAssertEqual(model.records.first?.requestData, raw.requestData, "records stay as recorded")
        model.query = "abc"
        XCTAssertEqual(model.visible, [], "masked values aren't searchable")
        model.query = "a@b.c"
        XCTAssertEqual(model.visible.count, 1)
        XCTAssertTrue(defaults.bool(forKey: InspectorModel.maskingKey))
        XCTAssertTrue(InspectorModel(source: { store }, defaults: defaults).maskingEnabled)

        model.maskingEnabled = false
        XCTAssertEqual(model.display(raw).requestData, raw.requestData)
    }

    func testMaskingKeepsUpWithContinuousTraffic() async throws {
        let store = MessageStore(maxEntries: 500)
        let model = InspectorModel(source: { store }, coalesce: 0, defaults: isolatedDefaults())
        model.maskingEnabled = true
        model.start()
        for i in 0..<200 { add(store, "h\(i)", data: #"{"token":"secret-\#(i)"}"#) }
        try await waitUntil { model.records.count == 200 }
        XCTAssertTrue(model.records.allSatisfy { model.display($0).requestData == #"{"token":"***"}"# })
    }

    private func add(_ store: MessageStore, _ handler: String, data: String?) {
        store.add { id, now in
            MessageRecord(id: id, requestId: nil, handlerName: handler, direction: .webToApp, requestData: data, status: .success, requestTimestamp: now)
        }
    }

    private func isolatedDefaults() -> UserDefaults {
        UserDefaults(suiteName: "dari.tests.\(UUID().uuidString)")!
    }

    private func waitUntil(_ condition: () -> Bool) async throws {
        for _ in 0..<100 where !condition() {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertTrue(condition())
    }
}
