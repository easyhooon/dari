@testable import Dari
import XCTest

final class MessageStoreTests: XCTestCase {
    private var clock: Int64 = 1_000

    func testResponseCompletesNewestPendingRequestOfSameDirection() {
        let store = MessageStore(maxEntries: 10) { [unowned self] in clock }
        add(store, "1", .webToApp)
        add(store, "1", .appToWeb)
        add(store, "1", .webToApp)
        clock = 1_250
        store.respond(requestId: "1", tag: nil, direction: .webToApp) { record, now in
            record.status = .success
            record.responseTimestamp = now
        }
        XCTAssertEqual(store.snapshot().map(\.status), [.inProgress, .inProgress, .success])
        XCTAssertEqual(store.snapshot().last?.responseTimestamp, 1_250)
    }

    func testDropsOldestBeyondMaxEntriesAndKeepsIds() {
        let store = MessageStore(maxEntries: 2)
        (1...3).forEach { add(store, "\($0)", .webToApp) }
        XCTAssertEqual(store.snapshot().map(\.id), [2, 3])
    }

    func testDropsMessagesOlderThanRetentionPeriodOnAdd() {
        let store = MessageStore(maxEntries: 10, retentionPeriod: 6) { [unowned self] in clock }
        add(store, "1", .webToApp)
        clock = 4_000
        add(store, "2", .webToApp)
        clock = 7_000
        add(store, "3", .webToApp)
        // Cutoff 1_000 keeps a message stamped exactly at it, as Android's `RetentionPolicy` does.
        XCTAssertEqual(store.snapshot().map(\.id), [1, 2, 3])
        clock = 10_000
        add(store, "4", .webToApp)
        XCTAssertEqual(store.snapshot().map(\.id), [2, 3, 4])
    }

    func testKeepsOldMessagesWithoutRetentionPeriod() {
        let store = MessageStore(maxEntries: 10) { [unowned self] in clock }
        add(store, "1", .webToApp)
        clock = 1_000_000_000
        add(store, "2", .webToApp)
        XCTAssertEqual(store.snapshot().map(\.id), [1, 2])
    }

    func testObserversSeeEventsInOrder() {
        let store = MessageStore(maxEntries: 10)
        var events: [String] = []
        let token = store.subscribe { event in
            switch event {
            case .added: events.append("added")
            case .updated: events.append("updated")
            case .cleared: events.append("cleared")
            }
        }
        add(store, "1", .webToApp)
        store.respond(requestId: "1", tag: nil, direction: .webToApp) { record, _ in record.status = .error }
        store.clear()
        store.unsubscribe(token)
        add(store, "2", .webToApp)
        XCTAssertEqual(events, ["added", "updated", "cleared"])
    }

    func testTruncatesLikeKotlin() {
        let (text, truncated) = MessageRecord.truncate("abcdef", maxLength: 3)
        XCTAssertTrue(truncated)
        XCTAssertEqual(text, "abc\n\n...[truncated, original length: 6 chars]")
        XCTAssertEqual(MessageRecord.truncate("abc", maxLength: 3).0, "abc")
    }

    func testSanitizesPageUrl() {
        XCTAssertEqual(MessageRecord.sanitizePageUrl("https://user:pw@example.com/a?b=1#c"), "https://example.com/a")
        XCTAssertNil(MessageRecord.sanitizePageUrl("?x"))
    }

    private func add(_ store: MessageStore, _ requestId: String, _ direction: MessageDirection) {
        store.add { id, now in
            MessageRecord(id: id, requestId: requestId, handlerName: "h", direction: direction, status: .inProgress, requestTimestamp: now)
        }
    }
}
