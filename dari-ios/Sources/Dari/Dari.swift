import Foundation

/// Settings for [Dari.start]. Mirrors the parts of Android's `DariConfig` that apply without an in-app UI.
public struct DariConfiguration {
    /// Messages kept in memory; older ones are dropped first.
    public var maxEntries: Int
    /// Payloads longer than this many UTF-16 units are truncated, as on Android.
    public var maxContentLength: Int
    /// When true, requests are recorded as finished right away unless a call says otherwise.
    public var fireAndForget: Bool
    /// Loopback ports tried in order. The desktop probes the same span.
    public var ports: ClosedRange<UInt16>

    public init(
        maxEntries: Int = 500,
        maxContentLength: Int = 500_000,
        fireAndForget: Bool = false,
        ports: ClosedRange<UInt16> = Dari.defaultPorts
    ) {
        self.maxEntries = maxEntries
        self.maxContentLength = maxContentLength
        self.fireAndForget = fireAndForget
        self.ports = ports
    }
}

/// Records WKWebView bridge traffic and serves it to the Dari desktop app.
///
/// Call [start] only in debug builds: anything that can reach the device's loopback can read the messages.
public final class Dari {
    public static let shared = Dari()

    /// The Dari desktop app probes these ports on 127.0.0.1.
    public static let defaultPorts: ClosedRange<UInt16> = 38_670...38_677

    /// Reported to the desktop as `dariVersion`.
    public static let version = "3.0.0"

    private let lock = NSLock()
    private var store: MessageStore?
    private var listener: InspectorListener?
    private var configuration = DariConfiguration()
    private var environment: [String: String] = [:]

    init() {}

    /// Starts recording and listening for the desktop. Calling it again while running does nothing.
    public func start(_ configuration: DariConfiguration = DariConfiguration()) throws {
        try lock.withLock {
            guard listener == nil else { return }
            let store = MessageStore(maxEntries: configuration.maxEntries)
            let listener = InspectorListener(ports: configuration.ports) { [weak self] channel in
                guard let self else { return }
                InspectorSession(channel: channel, appInfo: { [weak self] in self?.appInfo() ?? AppInfo.current([:]) }, store: store).run()
            }
            try listener.start()
            self.configuration = configuration
            self.store = store
            self.listener = listener
        }
    }

    /// Stops listening, drops connected desktops and discards recorded messages.
    public func stop() {
        lock.withLock {
            listener?.stop()
            listener = nil
            store = nil
        }
    }

    /// The port the listener bound, or nil when not started.
    public var port: UInt16? {
        lock.withLock { listener?.port }
    }

    /// Labels shown in the desktop's app info, like Android's `Dari.setEnvironment`.
    public func setEnvironment(_ labels: [String: String]) {
        lock.withLock { environment = labels }
    }

    /// Creates an interceptor for one bridge. Use one per WKWebView so page URLs stay separate.
    public func interceptor(tag: String? = nil) -> DariInterceptor {
        DariInterceptor(tag: tag, dari: self)
    }

    func record(_ body: (MessageStore, DariConfiguration) -> Void) {
        guard let (store, configuration) = lock.withLock({ store.map { ($0, configuration) } }) else { return }
        body(store, configuration)
    }

    private func appInfo() -> AppInfo {
        AppInfo.current(lock.withLock { environment })
    }
}

/// Explicit capture points for a bridge, mirroring Android's `DariInterceptor`.
/// Calls before [Dari.start] or after [Dari.stop] are ignored.
public final class DariInterceptor {
    public let tag: String?
    private weak var dari: Dari?
    private let lock = NSLock()
    private var pageUrl: String?

    init(tag: String?, dari: Dari) {
        self.tag = tag
        self.dari = dari
    }

    /// Requests captured afterwards record this URL without its query, fragment or user info.
    public func onPageUrlChanged(_ url: String?) {
        lock.withLock { pageUrl = MessageRecord.sanitizePageUrl(url) }
    }

    /// A Web → App request. A nil [requestId] records a standalone message that is never paired.
    public func onWebToAppRequest(
        handlerName: String,
        displayName: String? = nil,
        requestId: String?,
        requestData: String?,
        fireAndForget: Bool? = nil
    ) {
        addRequest(.webToApp, handlerName, displayName, requestId, requestData, fireAndForget)
    }

    /// The app's response to a Web → App request.
    public func onWebToAppResponse(handlerName: String, requestId: String?, responseData: String?, isSuccess: Bool) {
        addResponse(.webToApp, requestId, responseData, isSuccess)
    }

    /// An App → Web call, e.g. through `evaluateJavaScript`.
    public func onAppToWebRequest(
        handlerName: String,
        displayName: String? = nil,
        requestId: String?,
        data: String?,
        fireAndForget: Bool? = nil
    ) {
        addRequest(.appToWeb, handlerName, displayName, requestId, data, fireAndForget)
    }

    /// The web's response to an App → Web call.
    public func onAppToWebResponse(requestId: String?, isSuccess: Bool, responseData: String?) {
        addResponse(.appToWeb, requestId, responseData, isSuccess)
    }

    private func addRequest(
        _ direction: MessageDirection,
        _ handlerName: String,
        _ displayName: String?,
        _ requestId: String?,
        _ data: String?,
        _ fireAndForget: Bool?
    ) {
        let pageUrl = lock.withLock { self.pageUrl }
        dari?.record { store, configuration in
            let (data, truncated) = MessageRecord.truncate(data, maxLength: configuration.maxContentLength)
            let finished = fireAndForget ?? configuration.fireAndForget
            store.add { id, now in
                MessageRecord(
                    id: id,
                    requestId: requestId,
                    handlerName: handlerName,
                    displayName: displayName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? displayName : nil,
                    direction: direction,
                    tag: tag,
                    requestData: data,
                    requestDataTruncated: truncated,
                    status: finished ? .success : .inProgress,
                    requestTimestamp: now,
                    pageUrl: pageUrl
                )
            }
        }
    }

    private func addResponse(_ direction: MessageDirection, _ requestId: String?, _ data: String?, _ isSuccess: Bool) {
        // Without a request id there is nothing to pair with, as on Android.
        guard let requestId else { return }
        dari?.record { store, configuration in
            let (data, truncated) = MessageRecord.truncate(data, maxLength: configuration.maxContentLength)
            store.respond(requestId: requestId, tag: tag, direction: direction) { record, now in
                record.responseData = data
                record.responseDataTruncated = truncated
                record.responseTimestamp = now
                record.status = isSuccess ? .success : .error
            }
        }
    }
}
