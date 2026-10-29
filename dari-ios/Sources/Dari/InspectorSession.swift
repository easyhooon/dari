import Foundation

/// Serves one desktop connection, mirroring Android's `InspectorSession`. [run] blocks until the client disconnects.
final class InspectorSession {
    private let channel: FrameChannel
    private let appInfo: () -> AppInfo
    private let store: MessageStore
    // Observe events and replies leave through one queue, so a slow desktop never blocks recording.
    private let writes: DispatchQueue
    private let lock = NSLock()
    private var streams: [String: Int] = [:]
    private var maskingEnabled = false

    init(channel: FrameChannel, appInfo: @escaping () -> AppInfo, store: MessageStore) {
        self.channel = channel
        self.appInfo = appInfo
        self.store = store
        writes = DispatchQueue(label: "dari.inspector.session.\(channel.fd)")
    }

    func run() {
        defer {
            // Unsubscribe outside the session lock: the store calls observers under its own lock.
            let tokens = lock.withLock { () -> [Int] in
                defer { streams.removeAll() }
                return Array(streams.values)
            }
            tokens.forEach(store.unsubscribe)
            // Let queued writes fail against the closed socket before the caller closes it.
            writes.sync {}
        }
        do {
            guard try handshake() else { return }
            while let data = try channel.readFrame() {
                switch try JSONDecoder().decode(IncomingFrame.self, from: data) {
                case let .invoke(requestId, op, input): handle(requestId: requestId, op: op, input: input)
                case let .cancel(requestId): if let token = lock.withLock({ streams.removeValue(forKey: requestId) }) { store.unsubscribe(token) }
                case .other: break
                }
            }
        } catch {
            // Malformed data or a dropped socket ends this session only.
        }
    }

    private func handshake() throws -> Bool {
        guard let hello = try channel.read(ClientHello.self) else { return false }
        let accepted = hello.protocolVersion == InspectorProtocol.version
        try channel.write(ServerHello(
            accepted: accepted,
            protocolVersion: InspectorProtocol.version,
            rejection: accepted ? nil : "UNSUPPORTED_PROTOCOL_VERSION",
            app: appInfo()
        ))
        return accepted
    }

    private func handle(requestId: String, op: String, input: JSONValue) {
        do {
            switch op {
            case InspectorOperation.list:
                let limit = max(0, try input.decode(ListInput.self).limit)
                reply(requestId, ListOutput(messages: store.snapshot().suffix(limit).map { prepared($0).summary }))
            case InspectorOperation.detail:
                let id = try input.decode(DetailInput.self).id
                if let record = store.snapshot().first(where: { $0.id == id }) {
                    reply(requestId, prepared(record).detail)
                } else {
                    fail(requestId, InspectorError.notFound, "No message with id \(id)")
                }
            case InspectorOperation.search:
                let query = try input.decode(SearchInput.self).query.trimmingCharacters(in: .whitespacesAndNewlines)
                let ids = query.isEmpty ? [] : store.snapshot().map(prepared).filter { $0.matches(query) }.map(\.id)
                reply(requestId, SearchOutput(ids: ids))
            case InspectorOperation.observe:
                observe(requestId)
            case InspectorOperation.clear:
                store.clear()
                reply(requestId, EmptyObject())
            case InspectorOperation.settingsUpdate:
                let settings = try input.decode(InspectorSettings.self)
                lock.withLock { maskingEnabled = settings.maskingEnabled }
                reply(requestId, settings)
            default:
                fail(requestId, InspectorError.operationNotFound, "Unknown op \(op)")
            }
        } catch let error as DecodingError {
            fail(requestId, InspectorError.invalidInput, String(describing: error))
        } catch {
            fail(requestId, InspectorError.providerFailed, String(describing: error))
        }
    }

    // Subscribes before the next frame is read, so a following `list` can't miss events.
    // The store calls back under its lock, often on the main thread: only queue the event there.
    private func observe(_ requestId: String) {
        let token = store.subscribe { [weak self] event in
            self?.writes.async { self?.emit(requestId, event) }
        }
        lock.withLock { streams[requestId] = token }
    }

    // Runs on `writes`, in event order. Masking and encoding happen here, off the recording thread.
    private func emit(_ requestId: String, _ event: StoreEvent) {
        guard lock.withLock({ streams[requestId] != nil }) else { return }
        let output: ObserveEvent
        switch event {
        case let .added(record): output = ObserveEvent(type: "added", message: prepared(record).summary)
        case let .updated(record): output = ObserveEvent(type: "updated", message: prepared(record).summary)
        case .cleared: output = ObserveEvent(type: "cleared")
        }
        write(encode(ResultFrame(requestId: requestId, output: output, isFinal: false)))
    }

    private func prepared(_ record: MessageRecord) -> MessageRecord {
        lock.withLock { maskingEnabled } ? PayloadMasker.mask(record) : record
    }

    private func reply<T: Encodable>(_ requestId: String, _ output: T) {
        send(ResultFrame(requestId: requestId, output: output))
    }

    private func fail(_ requestId: String, _ code: String, _ message: String) {
        send(ResultFrame<EmptyObject>(requestId: requestId, error: InspectorError(code: code, message: message)))
    }

    private func send<T: Encodable>(_ frame: ResultFrame<T>) {
        let data = encode(frame)
        writes.async { [weak self] in self?.write(data) }
    }

    /// A reply that can't be encoded or exceeds the frame limit becomes an error, so the desktop isn't left waiting.
    private func encode<T: Encodable>(_ frame: ResultFrame<T>) -> Data {
        if let data = try? JSONEncoder().encode(frame), data.count <= InspectorProtocol.maxFrameBytes { return data }
        let error = InspectorError(code: InspectorError.providerFailed, message: "Reply could not be sent")
        // Encoding a small, fixed error frame can't fail.
        return (try? JSONEncoder().encode(ResultFrame<EmptyObject>(requestId: frame.requestId, error: error, isFinal: true))) ?? Data()
    }

    // A failed write means the desktop is gone; end the session as Android does instead of dropping frames.
    private func write(_ data: Data) {
        do {
            try channel.writeFrame(data)
        } catch {
            shutdown(channel.fd, SHUT_RDWR)
        }
    }
}
