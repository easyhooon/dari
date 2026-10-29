import Foundation

/// One captured bridge call. Mirrors `MessageEntry` without protobuf metadata.
struct MessageRecord: Equatable {
    let id: Int64
    var requestId: String?
    var handlerName: String
    var displayName: String?
    var direction: MessageDirection
    var tag: String?
    var requestData: String?
    var requestDataTruncated = false
    var responseData: String?
    var responseDataTruncated = false
    var status: MessageStatus
    var requestTimestamp: Int64
    var responseTimestamp: Int64?
    var pageUrl: String?
}

/// Raw values match `MessageDirection` in dari-core; the desktop filters on them.
enum MessageDirection: String {
    case webToApp = "WEB_TO_APP"
    case appToWeb = "APP_TO_WEB"
}

/// Raw values match `MessageStatus` in dari-core.
enum MessageStatus: String {
    case inProgress = "IN_PROGRESS"
    case success = "SUCCESS"
    case error = "ERROR"
}

enum StoreEvent: Equatable {
    case added(MessageRecord)
    case updated(MessageRecord)
    case cleared
}

/// Bounded in-memory buffer. Ids are stable for the life of the process, like Android's wire ids.
final class MessageStore {
    private let lock = NSLock()
    private var records: [MessageRecord] = []
    private var nextId: Int64 = 1
    private var observers: [Int: (StoreEvent) -> Void] = [:]
    private var nextObserver = 0
    private let maxEntries: Int
    private let now: () -> Int64

    init(maxEntries: Int, now: @escaping () -> Int64 = MessageStore.currentMillis) {
        self.maxEntries = max(1, maxEntries)
        self.now = now
    }

    static func currentMillis() -> Int64 {
        Int64((Date().timeIntervalSince1970 * 1000).rounded())
    }

    func snapshot() -> [MessageRecord] {
        lock.withLock { records }
    }

    func add(_ build: (_ id: Int64, _ now: Int64) -> MessageRecord) {
        lock.withLock {
            let record = build(nextId, now())
            nextId += 1
            records.append(record)
            if records.count > maxEntries { records.removeFirst(records.count - maxEntries) }
            notify(.added(record))
        }
    }

    /// Completes the newest matching request still waiting for a response, like Android's `responseTargets`.
    func respond(requestId: String, tag: String?, direction: MessageDirection, _ transform: (inout MessageRecord, Int64) -> Void) {
        lock.withLock {
            let matches = records.indices.filter {
                records[$0].requestId == requestId && records[$0].tag == tag && records[$0].direction == direction
            }
            let target = matches.last { records[$0].status == .inProgress } ?? matches.last { records[$0].responseTimestamp == nil }
            guard let index = target else { return }
            transform(&records[index], now())
            notify(.updated(records[index]))
        }
    }

    func clear() {
        lock.withLock {
            records.removeAll()
            notify(.cleared)
        }
    }

    /// [observer] runs under the store lock, in event order; it must only hand the event off.
    func subscribe(_ observer: @escaping (StoreEvent) -> Void) -> Int {
        lock.withLock {
            nextObserver += 1
            observers[nextObserver] = observer
            return nextObserver
        }
    }

    func unsubscribe(_ token: Int) {
        lock.withLock { _ = observers.removeValue(forKey: token) }
    }

    private func notify(_ event: StoreEvent) {
        observers.values.forEach { $0(event) }
    }
}

extension MessageRecord {
    static let truncationLimitDefault = 500_000

    /// Same marker as `MessageEntry.truncateIfNeeded`; lengths count UTF-16 units like Kotlin's `String.length`.
    static func truncate(_ data: String?, maxLength: Int) -> (String?, Bool) {
        guard let data else { return (nil, false) }
        let units = data.utf16.count
        guard units > maxLength else { return (data, false) }
        let head = String(decoding: Array(data.utf16.prefix(maxLength)), as: UTF16.self)
        return (head + "\n\n...[truncated, original length: \(units) chars]", true)
    }

    /// Drops query, fragment and user info, like `MessageEntry.sanitizePageUrl`.
    static func sanitizePageUrl(_ url: String?) -> String? {
        guard var url else { return nil }
        if let index = url.firstIndex(of: "#") { url = String(url[..<index]) }
        if let index = url.firstIndex(of: "?") { url = String(url[..<index]) }
        if let schemeEnd = url.range(of: "://") {
            let authorityEnd = url[schemeEnd.upperBound...].firstIndex(of: "/") ?? url.endIndex
            if let at = url[schemeEnd.upperBound..<authorityEnd].lastIndex(of: "@") {
                url.removeSubrange(schemeEnd.upperBound...at)
            }
        }
        return url.trimmingCharacters(in: .whitespaces).isEmpty ? nil : url
    }

    func matches(_ query: String) -> Bool {
        [handlerName, displayName, tag, requestId, pageUrl, requestData, responseData]
            .compactMap { $0 }
            .contains { $0.range(of: query, options: .caseInsensitive) != nil }
    }

    var summary: WireMessageSummary {
        WireMessageSummary(
            id: id,
            requestId: requestId,
            handlerName: handlerName,
            displayName: displayName,
            direction: direction.rawValue,
            tag: tag,
            status: status.rawValue,
            requestTimestamp: requestTimestamp,
            responseTimestamp: responseTimestamp,
            requestSizeBytes: requestData?.utf8.count ?? 0,
            responseSizeBytes: responseData?.utf8.count ?? 0,
            pageUrl: pageUrl
        )
    }

    var detail: WireMessageDetail {
        WireMessageDetail(
            summary: summary,
            requestData: requestData,
            responseData: responseData,
            requestDataTruncated: requestDataTruncated,
            responseDataTruncated: responseDataTruncated
        )
    }
}
