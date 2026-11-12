import Combine
import Dari
import Foundation

/// Feeds the inspector screens from the running [Dari] store.
@MainActor
final class InspectorModel: ObservableObject {
    /// Newest first, like the Android list.
    @Published private(set) var records: [MessageRecord] = []
    @Published private(set) var isRunning = false
    @Published var query = ""

    private let source: () -> MessageStore?
    private let coalesce: TimeInterval
    private var store: MessageStore?
    private var token: Int?
    private var reloadPending = false
    private var attached = false
    private var storeObserver: NSObjectProtocol?

    init(source: @escaping () -> MessageStore? = { Dari.shared.inspectorStore }, coalesce: TimeInterval = 0.15) {
        self.source = source
        self.coalesce = coalesce
        // Dari.stop() / start() replace the store; follow it while the inspector is open.
        storeObserver = NotificationCenter.default.addObserver(forName: Dari.storeDidChange, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.attached else { return }
                self.start()
            }
        }
    }

    deinit {
        storeObserver.map(NotificationCenter.default.removeObserver)
    }

    var visible: [MessageRecord] {
        let query = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return query.isEmpty ? records : records.filter { $0.matches(query) }
    }

    func record(id: Int64) -> MessageRecord? {
        records.first { $0.id == id }
    }

    /// Subscribes to the current store. `stop()` and a later `start()` on [Dari] replace the store, so call this on appear.
    func start() {
        stop()
        attached = true
        store = source()
        isRunning = store != nil
        // Retention and the maxEntries trim drop records without an event, so every event triggers a fresh snapshot.
        // The store calls observers under its lock: only hop off it here, never read the store.
        token = store?.subscribe { [weak self] _ in
            Task { @MainActor in self?.scheduleReload() }
        }
        reload()
    }

    func stop() {
        if let token { store?.unsubscribe(token) }
        token = nil
        store = nil
        attached = false
    }

    func reload() {
        records = store?.snapshot().reversed() ?? []
    }

    func clear() {
        store?.clear()
    }

    private func scheduleReload() {
        guard !reloadPending else { return }
        reloadPending = true
        DispatchQueue.main.asyncAfter(deadline: .now() + coalesce) { [weak self] in
            Task { @MainActor in
                self?.reloadPending = false
                self?.reload()
            }
        }
    }
}
