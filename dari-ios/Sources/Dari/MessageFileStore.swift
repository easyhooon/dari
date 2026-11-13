import Foundation

/// Keeps recorded messages in a file so they survive relaunches, like Android's Room store.
/// One `<id>\t<json>` line per added or updated record; the last line for an id wins. The id prefix lets
/// [lastId] find where ids continue without decoding any JSON. All file work runs on one serial queue
/// per file, in the order the store queued it, so an append never lands after a clear or a compaction.
final class MessageFileStore {
    static let formatVersion = 1

    let url: URL
    private let shared: Shared
    private var queue: DispatchQueue { shared.queue }
    /// Kept open between appends, so a write right before a crash has the best chance of landing. Used on [queue].
    private var handle: FileHandle? {
        get { shared.handle }
        set { shared.handle = newValue }
    }

    /// One queue and one open handle per file, shared by every instance: a store started after stop() then waits
    /// for the old store's writes, and a compaction by one instance can't leave another writing to the replaced file.
    private final class Shared {
        let queue: DispatchQueue
        var handle: FileHandle?
        init(_ url: URL) { queue = DispatchQueue(label: "dari.filestore.\(url.lastPathComponent)") }
    }

    private static let sharedLock = NSLock()
    private static var sharedByURL: [URL: Shared] = [:]

    init(url: URL) {
        self.url = url
        shared = Self.sharedLock.withLock {
            if let existing = Self.sharedByURL[url] { return existing }
            let created = Shared(url)
            Self.sharedByURL[url] = created
            return created
        }
    }

    /// `Application Support/Dari/messages.jsonl` in the app's container.
    static var defaultURL: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("Dari", isDirectory: true).appendingPathComponent("messages.jsonl")
    }

    /// The highest id in the file, after any queued writes have finished. Cheap: reads only the line prefixes.
    func lastId() -> Int64 {
        queue.sync {
            guard let data = try? Data(contentsOf: url, options: .mappedIfSafe) else { return 0 }
            // memchr over the mapped file; parsing only the digits before each tab keeps this cheap even at 100 MB+.
            return data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) -> Int64 in
                guard let base = raw.baseAddress else { return 0 }
                var last: Int64 = 0
                var offset = 0
                while offset < raw.count {
                    var id: Int64 = 0
                    var digits = 0
                    var cursor = offset
                    while cursor < raw.count, digits < 18, (48...57).contains(raw[cursor]) {
                        id = id * 10 + Int64(raw[cursor] - 48)
                        digits += 1
                        cursor += 1
                    }
                    if digits > 0, cursor < raw.count, raw[cursor] == 9 { last = max(last, id) }
                    guard let newline = memchr(base + cursor, 10, raw.count - cursor) else { break }
                    offset = base.distance(to: UnsafeRawPointer(newline)) + 1
                }
                return last
            }
        }
    }

    /// Decodes records from earlier launches, oldest first, on the file's queue, then hands them to [completion].
    /// Runs after any writes queued before it, and before any queued after.
    func load(_ completion: @escaping ([MessageRecord]) -> Void) {
        queue.async { [self] in
            // Only map the file here; decoding elsewhere keeps appends from waiting behind a large file.
            // The mapping keeps its bytes even if a later compaction replaces or deletes the file.
            let data = try? Data(contentsOf: url, options: .mappedIfSafe)
            DispatchQueue.global(qos: .utility).async { completion(Self.decodeAll(data)) }
        }
    }

    private static func decodeAll(_ data: Data?) -> [MessageRecord] {
        guard let data, !data.isEmpty else { return [] }
        var byId: [Int64: MessageRecord] = [:]
        let decoder = JSONDecoder()
        for line in data.split(separator: UInt8(ascii: "\n")) {
            // A crash mid-write can leave a partial last line; skip anything that doesn't decode.
            guard let tab = line.firstIndex(of: UInt8(ascii: "\t")),
                  let entry = try? decoder.decode(StoredLine.self, from: Data(line[line.index(after: tab)...]))
            else { continue }
            guard entry.v == formatVersion else { return [] }
            if let record = entry.record.record { byId[record.id] = record }
        }
        return byId.values.sorted { $0.id < $1.id }
    }

    func append(_ record: MessageRecord) {
        queue.async { [self] in write(record) }
    }

    /// Rewrites the file with [records] only, dropping lines for pruned or superseded records.
    func compact(_ records: [MessageRecord]) {
        queue.async { [self] in
            let encoder = JSONEncoder()
            var data = Data()
            for record in records {
                guard let line = Self.line(record, encoder) else { continue }
                data.append(line)
            }
            closeHandle()
            prepareDirectory()
            try? data.write(to: url, options: [.atomic])
            protect()
        }
    }

    func delete() {
        queue.async { [self] in
            closeHandle()
            try? FileManager.default.removeItem(at: url)
        }
    }

    /// Waits for queued writes, for tests.
    func flush() {
        queue.sync {}
    }

    private static func line(_ record: MessageRecord, _ encoder: JSONEncoder) -> Data? {
        // JSONEncoder escapes newlines inside strings, so a record is always one line.
        guard let json = try? encoder.encode(StoredLine(record: StoredRecord(record))) else { return nil }
        return Data("\(record.id)\t".utf8) + json + Data("\n".utf8)
    }

    private func write(_ record: MessageRecord) {
        guard let data = Self.line(record, JSONEncoder()) else { return }
        if handle == nil {
            prepareDirectory()
            if !FileManager.default.fileExists(atPath: url.path) {
                FileManager.default.createFile(atPath: url.path, contents: nil)
                protect()
            }
            handle = try? FileHandle(forUpdating: url)
            // A crash can leave a partial last line; start on a fresh line so this record isn't glued to it.
            if let handle, let end = try? handle.seekToEnd(), end > 0 {
                try? handle.seek(toOffset: end - 1)
                if (try? handle.read(upToCount: 1)) != Data("\n".utf8) {
                    _ = try? handle.seekToEnd()
                    try? handle.write(contentsOf: Data("\n".utf8))
                }
            }
        }
        // Seek every time: another instance for the same file may have appended since.
        _ = try? handle?.seekToEnd()
        try? handle?.write(contentsOf: data)
    }

    private func closeHandle() {
        try? handle?.close()
        handle = nil
    }

    private func prepareDirectory() {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    }

    /// Payloads can hold tokens: keep the file out of backups and encrypted while the device is locked after boot.
    private func protect() {
        var url = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
        #if os(iOS)
        try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: url.path)
        #endif
    }
}

/// Versioned on-disk shape, kept apart from the internal types so they can change freely.
private struct StoredLine: Codable {
    var v = MessageFileStore.formatVersion
    let record: StoredRecord
}

private struct StoredRecord: Codable {
    struct Payload: Codable {
        let originalSizeBytes: Int
        let decodeStatus: String
        let rawPreviewBase64: String?
        let rawPreviewSizeBytes: Int?
        let rawPreviewTruncated: Bool?

        init(_ metadata: PayloadMetadata) {
            originalSizeBytes = metadata.originalSizeBytes
            decodeStatus = metadata.decodeStatus.rawValue
            rawPreviewBase64 = metadata.rawPreview?.base64
            rawPreviewSizeBytes = metadata.rawPreview?.sizeBytes
            rawPreviewTruncated = metadata.rawPreview?.truncated
        }

        var metadata: PayloadMetadata? {
            guard let status = PayloadMetadata.DecodeStatus(rawValue: decodeStatus) else { return nil }
            let preview = rawPreviewBase64.map {
                PayloadMetadata.RawPreview(base64: $0, sizeBytes: rawPreviewSizeBytes ?? 0, truncated: rawPreviewTruncated ?? false)
            }
            return PayloadMetadata(originalSizeBytes: originalSizeBytes, decodeStatus: status, rawPreview: preview)
        }
    }

    let id: Int64
    let requestId: String?
    let handlerName: String
    let displayName: String?
    let direction: String
    let tag: String?
    let requestData: String?
    let requestDataTruncated: Bool
    let responseData: String?
    let responseDataTruncated: Bool
    let requestPayload: Payload?
    let responsePayload: Payload?
    let status: String
    let requestTimestamp: Int64
    let responseTimestamp: Int64?
    let pageUrl: String?

    init(_ record: MessageRecord) {
        id = record.id
        requestId = record.requestId
        handlerName = record.handlerName
        displayName = record.displayName
        direction = record.direction.rawValue
        tag = record.tag
        requestData = record.requestData
        requestDataTruncated = record.requestDataTruncated
        responseData = record.responseData
        responseDataTruncated = record.responseDataTruncated
        requestPayload = record.requestPayloadMetadata.map(Payload.init)
        responsePayload = record.responsePayloadMetadata.map(Payload.init)
        status = record.status.rawValue
        requestTimestamp = record.requestTimestamp
        responseTimestamp = record.responseTimestamp
        pageUrl = record.pageUrl
    }

    var record: MessageRecord? {
        guard let direction = MessageDirection(rawValue: direction), let status = MessageStatus(rawValue: status) else { return nil }
        return MessageRecord(
            id: id,
            requestId: requestId,
            handlerName: handlerName,
            displayName: displayName,
            direction: direction,
            tag: tag,
            requestData: requestData,
            requestDataTruncated: requestDataTruncated,
            responseData: responseData,
            responseDataTruncated: responseDataTruncated,
            requestPayloadMetadata: requestPayload?.metadata,
            responsePayloadMetadata: responsePayload?.metadata,
            status: status,
            requestTimestamp: requestTimestamp,
            responseTimestamp: responseTimestamp,
            pageUrl: pageUrl
        )
    }
}
