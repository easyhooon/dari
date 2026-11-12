@testable import Dari
import XCTest

final class MessageFileStoreTests: XCTestCase {
    private var url: URL!
    private var clock: Int64 = 1_000

    override func setUp() {
        url = FileManager.default.temporaryDirectory
            .appendingPathComponent("dari-filestore-\(UUID().uuidString)", isDirectory: true)
            .appendingPathComponent("messages.jsonl")
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
    }

    func testFullRecordSurvivesARelaunch() {
        let first = store()
        first.add { id, now in
            MessageRecord(
                id: id, requestId: "r1", handlerName: "greet", displayName: "Greet", direction: .appToWeb, tag: "main",
                requestData: "{\"a\":1}\nsecond line", requestDataTruncated: true,
                requestPayloadMetadata: PayloadMetadata(
                    originalSizeBytes: 4, decodeStatus: .decoded,
                    rawPreview: .init(base64: "CgJoaQ==", sizeBytes: 4, truncated: false)
                ),
                status: .inProgress, requestTimestamp: now, pageUrl: "https://a.b/c"
            )
        }
        clock = 1_250
        first.respond(requestId: "r1", tag: "main", direction: .appToWeb) { record, now in
            record.responseData = "ok"
            record.responsePayloadMetadata = PayloadMetadata(originalSizeBytes: 2, decodeStatus: .failed, rawPreview: nil)
            record.status = .error
            record.responseTimestamp = now
        }
        first.add { id, now in
            MessageRecord(id: id, requestId: nil, handlerName: "log", direction: .webToApp, status: .success, requestTimestamp: now)
        }
        flush()

        XCTAssertEqual(loaded().snapshot(), first.snapshot())
    }

    func testReloadAppliesRetentionAndMaxEntriesAndContinuesIds() {
        let first = store(maxEntries: 10)
        for _ in 0..<4 { add(first, "x") }
        clock = 50_000
        add(first, "recent")
        flush()

        let second = loaded(maxEntries: 10, retentionPeriod: 10)
        XCTAssertEqual(second.snapshot().map(\.handlerName), ["recent"])
        add(second, "new")
        XCTAssertEqual(second.snapshot().map(\.id), [5, 6])

        XCTAssertEqual(loaded(maxEntries: 2).snapshot().map(\.id), [5, 6], "trimmed to the newest maxEntries")
    }

    func testResponsesDontPairWithEarlierLaunches() {
        let first = store()
        add(first, "pending", requestId: "app_1")
        flush()

        let second = loaded()
        second.respond(requestId: "app_1", tag: nil, direction: .webToApp) { record, _ in record.status = .success }
        XCTAssertEqual(second.snapshot().first?.status, .inProgress)
        add(second, "live", requestId: "app_1")
        second.respond(requestId: "app_1", tag: nil, direction: .webToApp) { record, _ in record.status = .success }
        XCTAssertEqual(second.snapshot().map(\.status), [.inProgress, .success])
    }

    func testSkipsAPartialLastLineAndRejectsUnknownVersions() throws {
        add(store(), "kept")
        flush()
        let handle = try FileHandle(forWritingTo: url)
        try handle.seekToEnd()
        try handle.write(contentsOf: Data("{\"v\":1,\"record\":{\"id\":9".utf8))
        try handle.close()
        XCTAssertEqual(loaded().snapshot().map(\.handlerName), ["kept"])

        try Data("{\"v\":99,\"record\":{}}\n".utf8).write(to: url)
        XCTAssertEqual(loaded().snapshot(), [])
    }

    func testClearDeletesTheFileAndCompactionBoundsIt() throws {
        let persisted = store(maxEntries: 3)
        for i in 0..<40 { add(persisted, "h\(i)") }
        flush()
        let lines = try String(contentsOf: url, encoding: .utf8).split(separator: "\n").count
        XCTAssertLessThanOrEqual(lines, 3 * 4 + 3, "compaction drops lines for trimmed records")
        XCTAssertEqual(loaded(maxEntries: 3).snapshot().map(\.handlerName), ["h37", "h38", "h39"])

        persisted.clear()
        flush()
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }

    func testStartWithoutPersistenceDeletesAnEarlierFile() throws {
        let dari = Dari()
        var configuration = DariConfiguration(persistsMessages: true, ports: 38_730...38_737)
        configuration.persistenceURL = url
        try dari.start(configuration)
        dari.interceptor().onWebToAppRequest(handlerName: "kept", requestId: nil, requestData: nil)
        dari.stop()
        try dari.start(configuration)
        flush()
        XCTAssertEqual(dari.records.map(\.handlerName), ["kept"], "stop() keeps the file for the next start")
        dari.stop()

        configuration.persistsMessages = false
        try dari.start(configuration)
        dari.stop()
        flush()
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }

    func testRecordsWhileLoadingAndMergesInIdOrder() {
        let first = store()
        add(first, "old1")
        add(first, "old2")
        flush()

        // Records right away, before the stored records are decoded; ids continue after them.
        let second = store()
        var events: [String] = []
        let token = second.subscribe { event in
            switch event {
            case .cleared: events.append("cleared")
            case let .added(record): events.append(record.handlerName)
            case .updated: break
            }
        }
        add(second, "live")
        flush()
        second.unsubscribe(token)
        XCTAssertEqual(second.snapshot().map(\.handlerName), ["old1", "old2", "live"])
        XCTAssertEqual(second.snapshot().map(\.id), [1, 2, 3])
        XCTAssertEqual(events.suffix(4), ["cleared", "old1", "old2", "live"], "observers rebuild in id order")
    }

    func testClearWhileLoadingDropsStoredRecords() {
        let first = store()
        add(first, "old")
        flush()
        let second = store()
        second.clear()
        flush()
        XCTAssertEqual(second.snapshot(), [])
        XCTAssertEqual(loaded().snapshot(), [])
    }

    /// A store whose stored records have been merged.
    private func loaded(maxEntries: Int = 100, retentionPeriod: TimeInterval? = nil) -> MessageStore {
        let store = store(maxEntries: maxEntries, retentionPeriod: retentionPeriod)
        flush()
        return store
    }

    private func store(maxEntries: Int = 100, retentionPeriod: TimeInterval? = nil) -> MessageStore {
        MessageStore(maxEntries: maxEntries, retentionPeriod: retentionPeriod, fileStore: MessageFileStore(url: url)) { [unowned self] in clock }
    }

    private func add(_ store: MessageStore, _ handler: String, requestId: String? = nil) {
        store.add { id, now in
            MessageRecord(id: id, requestId: requestId, handlerName: handler, direction: .webToApp, status: .inProgress, requestTimestamp: now)
        }
    }

    private func flush() {
        MessageFileStore(url: url).flush()
    }
}
