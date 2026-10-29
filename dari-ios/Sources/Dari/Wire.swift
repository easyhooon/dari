import Foundation

// Mirrors dari-inspector-protocol. Field names and enum strings must match the Kotlin serializers exactly;
// Tests/DariTests/Fixtures holds JSON generated from them.

enum InspectorProtocol {
    /** Exact-match version, same as `InspectorProtocol.VERSION` in Kotlin. */
    static let version = 1
    static let maxFrameBytes = 4 * 1024 * 1024
}

enum InspectorOperation {
    static let list = "messages.list"
    static let detail = "messages.detail"
    static let observe = "messages.observe"
    static let clear = "messages.clear"
    static let settingsUpdate = "settings.update"
    static let search = "messages.search"
}

struct ClientHello: Codable, Equatable {
    let protocolVersion: Int
    let clientName: String
    let clientVersion: String
}

struct ServerHello: Codable, Equatable {
    let accepted: Bool
    let protocolVersion: Int
    var rejection: String?
    var app: AppInfo?
}

struct AppInfo: Codable, Equatable {
    let applicationId: String
    let appName: String
    let appVersion: String
    let dariVersion: String
    let deviceModel: String
    /** Android API level; always 0 on iOS. */
    let sdkInt: Int
    var environment: [String: String]?
    var platform: String?
    var osVersion: String?
}

struct WireMessageSummary: Codable, Equatable {
    let id: Int64
    var requestId: String?
    let handlerName: String
    var displayName: String?
    let direction: String
    var tag: String?
    let status: String
    let requestTimestamp: Int64
    var responseTimestamp: Int64?
    let requestSizeBytes: Int
    let responseSizeBytes: Int
    var pageUrl: String?
}

struct WireMessageDetail: Codable, Equatable {
    let summary: WireMessageSummary
    var requestData: String?
    var responseData: String?
    var requestDataTruncated: Bool = false
    var responseDataTruncated: Bool = false
}

struct InspectorSettings: Codable, Equatable {
    let maskingEnabled: Bool
}

struct ListInput: Codable {
    var limit: Int = 500
}

struct ListOutput: Codable, Equatable {
    let messages: [WireMessageSummary]
}

struct DetailInput: Codable {
    let id: Int64
}

struct SearchInput: Codable {
    let query: String
}

struct SearchOutput: Codable, Equatable {
    let ids: [Int64]
}

struct InspectorError: Codable, Equatable {
    let code: String
    let message: String

    static let invalidInput = "INVALID_INPUT"
    static let operationNotFound = "OPERATION_NOT_FOUND"
    static let notFound = "NOT_FOUND"
    static let providerFailed = "PROVIDER_FAILED"
}

/** A `messages.observe` event: `{"type":"added","message":{…}}` or `{"type":"cleared"}`. */
struct ObserveEvent: Encodable, Equatable {
    let type: String
    var message: WireMessageSummary?
}

/** Desktop → app frames. Results from the desktop are ignored, as on Android. */
enum IncomingFrame {
    case invoke(requestId: String, op: String, input: JSONValue)
    case cancel(requestId: String)
    case other
}

extension IncomingFrame: Decodable {
    private enum Keys: String, CodingKey { case type, requestId, op, input }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: Keys.self)
        switch try container.decode(String.self, forKey: .type) {
        case "invoke":
            self = .invoke(
                requestId: try container.decode(String.self, forKey: .requestId),
                op: try container.decode(String.self, forKey: .op),
                input: try container.decodeIfPresent(JSONValue.self, forKey: .input) ?? .object([:])
            )
        case "cancel":
            self = .cancel(requestId: try container.decode(String.self, forKey: .requestId))
        default:
            self = .other
        }
    }
}

/** App → desktop `result` frame. `output` and `error` are omitted when nil. */
struct ResultFrame<Output: Encodable>: Encodable {
    let type = "result"
    let requestId: String
    var output: Output?
    var error: InspectorError?
    var isFinal = true
}

struct EmptyObject: Codable, Equatable {}

/** Untyped JSON, used for `invoke` inputs that are decoded once the op is known. */
enum JSONValue: Codable, Equatable {
    case null
    case bool(Bool)
    case int(Int64)
    case double(Double)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Int64.self) {
            self = .int(value)
        } else if let value = try? container.decode(Double.self) {
            self = .double(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode([JSONValue].self) {
            self = .array(value)
        } else {
            self = .object(try container.decode([String: JSONValue].self))
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .null: try container.encodeNil()
        case let .bool(value): try container.encode(value)
        case let .int(value): try container.encode(value)
        case let .double(value): try container.encode(value)
        case let .string(value): try container.encode(value)
        case let .array(value): try container.encode(value)
        case let .object(value): try container.encode(value)
        }
    }

    func decode<T: Decodable>(_ type: T.Type) throws -> T {
        try JSONDecoder().decode(T.self, from: JSONEncoder().encode(self))
    }
}
