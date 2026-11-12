import Foundation

/// Settings for [Dari.start]. Mirrors the parts of Android's `DariConfig` that apply without an in-app UI.
public struct DariConfiguration {
    /// Messages kept in memory; older ones are dropped first.
    public var maxEntries: Int
    /// Messages older than this many seconds are dropped when a new one is recorded. Nil keeps them until `maxEntries`.
    public var retentionPeriod: TimeInterval?
    /// Payloads longer than this many UTF-16 units are truncated, as on Android.
    public var maxContentLength: Int
    /// When true, requests are recorded as finished right away unless a call says otherwise.
    public var fireAndForget: Bool
    /// Loopback ports tried in order. The desktop probes the same span.
    public var ports: ClosedRange<UInt16>
    /// When set, WKWebView bridge handlers and `evaluateJavaScript` calls are recorded without a [DariInterceptor].
    public var autoCapture: DariAutoCapture?

    public init(
        maxEntries: Int = 500,
        retentionPeriod: TimeInterval? = nil,
        maxContentLength: Int = 500_000,
        fireAndForget: Bool = false,
        ports: ClosedRange<UInt16> = Dari.defaultPorts,
        autoCapture: DariAutoCapture? = nil
    ) {
        self.maxEntries = maxEntries
        self.retentionPeriod = retentionPeriod
        self.maxContentLength = maxContentLength
        self.fireAndForget = fireAndForget
        self.ports = ports
        self.autoCapture = autoCapture
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
    public static let version = "3.5.0"

    private let lock = NSLock()
    private var store: MessageStore?
    private var listener: InspectorListener?
    private var configuration = DariConfiguration()
    private var environment: [String: String] = [:]
    private var payloadRedactor: (@Sendable (DariMessage) throws -> DariMessage)?

    init() {}

    /// Starts recording and listening for the desktop. Calling it again while running does nothing.
    public func start(_ configuration: DariConfiguration = DariConfiguration()) throws {
        precondition(configuration.retentionPeriod.map { $0 > 0 } ?? true, "retentionPeriod must be positive when set")
        try lock.withLock {
            guard listener == nil else { return }
            let store = MessageStore(maxEntries: configuration.maxEntries, retentionPeriod: configuration.retentionPeriod)
            let listener = InspectorListener(ports: configuration.ports) { [weak self] channel in
                guard let self else { return }
                InspectorSession(
                    channel: channel,
                    appInfo: { [weak self] in self?.appInfo() ?? AppInfo.current([:]) },
                    store: store,
                    redact: { [weak self] in self?.redact($0) ?? $0 }
                ).run()
            }
            try listener.start()
            self.configuration = configuration
            self.store = store
            self.listener = listener
        }
        if configuration.autoCapture != nil { WebKitHooks.install() }
        NotificationCenter.default.post(name: Dari.storeDidChange, object: self)
    }

    /// Stops listening, drops connected desktops and discards recorded messages.
    public func stop() {
        lock.withLock {
            listener?.stop()
            listener = nil
            store = nil
        }
        NotificationCenter.default.post(name: Dari.storeDidChange, object: self)
    }

    /// Posted after [start] or [stop], so an open in-app inspector follows the new store.
    package static let storeDidChange = Notification.Name("dari.storeDidChange")

    /// The port the listener bound, or nil when not started.
    public var port: UInt16? {
        lock.withLock { listener?.port }
    }

    /// Auto-capture settings while running, nil otherwise.
    var autoCapture: DariAutoCapture? {
        lock.withLock { listener == nil ? nil : configuration.autoCapture }
    }

    /// Labels shown in the desktop's app info, like Android's `Dari.setEnvironment`.
    public func setEnvironment(_ labels: [String: String]) {
        lock.withLock { environment = labels }
    }

    /// Runs on every message sent to the desktop app, after the built-in masking when the desktop turns it on.
    /// Throwing hides the fields it could edit. Called on background threads, possibly concurrently; recorded messages stay as they are.
    public func setPayloadRedactor(_ redactor: (@Sendable (DariMessage) throws -> DariMessage)?) {
        lock.withLock { payloadRedactor = redactor }
    }

    func redact(_ record: MessageRecord) -> MessageRecord {
        guard let redactor = lock.withLock({ payloadRedactor }) else { return record }
        return record.redacted(by: redactor)
    }

    /// Creates an interceptor for one bridge. Use one per WKWebView so page URLs stay separate.
    /// [protobufDecoder] renders the bytes passed to the `…Protobuf…` methods.
    public func interceptor(tag: String? = nil, protobufDecoder: DariProtobufDecoder? = nil) -> DariInterceptor {
        DariInterceptor(tag: tag, protobufDecoder: protobufDecoder, dari: self)
    }

    /// The running store for the in-app inspector, nil when not started.
    package var inspectorStore: MessageStore? {
        lock.withLock { store }
    }

    /// App details for the in-app inspector's export header.
    package var inspectorAppInfo: AppInfo {
        appInfo()
    }

    /// Recorded messages, for tests.
    var records: [MessageRecord] {
        lock.withLock { store?.snapshot() ?? [] }
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
    private let protobufDecoder: DariProtobufDecoder?
    private weak var dari: Dari?
    private let lock = NSLock()
    private var pageUrl: String?

    init(tag: String?, protobufDecoder: DariProtobufDecoder? = nil, dari: Dari) {
        self.tag = tag
        self.protobufDecoder = protobufDecoder
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
        addRequest(.webToApp, handlerName, displayName, requestId, fireAndForget) { Self.text(requestData, $0) }
    }

    /// The app's response to a Web → App request.
    public func onWebToAppResponse(handlerName: String, requestId: String?, responseData: String?, isSuccess: Bool) {
        addResponse(.webToApp, requestId, isSuccess) { Self.text(responseData, $1) }
    }

    /// An App → Web call, e.g. through `evaluateJavaScript`.
    public func onAppToWebRequest(
        handlerName: String,
        displayName: String? = nil,
        requestId: String?,
        data: String?,
        fireAndForget: Bool? = nil
    ) {
        addRequest(.appToWeb, handlerName, displayName, requestId, fireAndForget) { Self.text(data, $0) }
    }

    /// The web's response to an App → Web call.
    public func onAppToWebResponse(requestId: String?, isSuccess: Bool, responseData: String?) {
        addResponse(.appToWeb, requestId, isSuccess) { Self.text(responseData, $1) }
    }

    /// A Web → App request carrying protobuf bytes, shown decoded by the interceptor's [DariProtobufDecoder].
    public func onWebToAppProtobufRequest(
        handlerName: String,
        displayName: String? = nil,
        requestId: String?,
        requestData: Data,
        fireAndForget: Bool? = nil
    ) {
        addRequest(.webToApp, handlerName, displayName, requestId, fireAndForget) { [protobufDecoder] max in
            Self.rendered(requestData, protobufDecoder?.decoder(handlerName: handlerName, direction: .webToApp, part: .request), max)
        }
    }

    public func onWebToAppProtobufResponse(handlerName: String, requestId: String?, responseData: Data, isSuccess: Bool) {
        addResponse(.webToApp, requestId, isSuccess) { [protobufDecoder] _, max in
            Self.rendered(responseData, protobufDecoder?.decoder(handlerName: handlerName, direction: .webToApp, part: .response), max)
        }
    }

    public func onAppToWebProtobufRequest(
        handlerName: String,
        displayName: String? = nil,
        requestId: String?,
        data: Data,
        fireAndForget: Bool? = nil
    ) {
        addRequest(.appToWeb, handlerName, displayName, requestId, fireAndForget) { [protobufDecoder] max in
            Self.rendered(data, protobufDecoder?.decoder(handlerName: handlerName, direction: .appToWeb, part: .request), max)
        }
    }

    /// The handler name comes from the matching request, as on Android.
    public func onAppToWebProtobufResponse(requestId: String?, isSuccess: Bool, responseData: Data) {
        addResponse(.appToWeb, requestId, isSuccess, needsTarget: true) { [protobufDecoder] handlerName, max in
            let decode = handlerName.flatMap { protobufDecoder?.decoder(handlerName: $0, direction: .appToWeb, part: .response) }
            return Self.rendered(responseData, decode, max)
        }
    }

    private typealias Payload = (data: String?, truncated: Bool, metadata: PayloadMetadata?)

    private static func text(_ data: String?, _ maxLength: Int) -> Payload {
        let (data, truncated) = MessageRecord.truncate(data, maxLength: maxLength)
        return (data, truncated, nil)
    }

    private static func rendered(_ payload: Data, _ decode: DariProtobufDecoder.Decode?, _ maxLength: Int) -> Payload {
        let rendered = ProtobufRenderer.render(payload, decode: decode, maxContentLength: maxLength)
        return (rendered.text, rendered.truncated, rendered.metadata)
    }

    // [payload] runs before the store lock is taken, so an app decoder can't block or re-enter recording.
    private func addRequest(
        _ direction: MessageDirection,
        _ handlerName: String,
        _ displayName: String?,
        _ requestId: String?,
        _ fireAndForget: Bool?,
        _ payload: (_ maxLength: Int) -> Payload
    ) {
        let pageUrl = lock.withLock { self.pageUrl }
        dari?.record { store, configuration in
            let payload = payload(configuration.maxContentLength)
            let finished = fireAndForget ?? configuration.fireAndForget
            store.add { id, now in
                MessageRecord(
                    id: id,
                    requestId: requestId,
                    handlerName: handlerName,
                    displayName: displayName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? displayName : nil,
                    direction: direction,
                    tag: tag,
                    requestData: payload.data,
                    requestDataTruncated: payload.truncated,
                    requestPayloadMetadata: payload.metadata,
                    status: finished ? .success : .inProgress,
                    requestTimestamp: now,
                    pageUrl: pageUrl
                )
            }
        }
    }

    private func addResponse(
        _ direction: MessageDirection,
        _ requestId: String?,
        _ isSuccess: Bool,
        needsTarget: Bool = false,
        _ payload: (_ handlerName: String?, _ maxLength: Int) -> Payload
    ) {
        // Without a request id there is nothing to pair with, as on Android.
        guard let requestId else { return }
        dari?.record { store, configuration in
            // The lock is released while decoding, so complete the record the decoder was chosen for.
            let target = needsTarget ? store.responseTarget(requestId: requestId, tag: tag, direction: direction) : nil
            let payload = payload(target?.handlerName, configuration.maxContentLength)
            store.respond(requestId: requestId, tag: tag, direction: direction, id: target?.id) { record, now in
                record.responseData = payload.data
                record.responseDataTruncated = payload.truncated
                record.responsePayloadMetadata = payload.metadata
                record.responseTimestamp = now
                record.status = isSuccess ? .success : .error
            }
        }
    }
}
