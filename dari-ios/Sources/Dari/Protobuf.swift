import Foundation

/// Turns protobuf bytes into display text per handler, like Android's `protobufDecoder { }`.
/// Dari has no SwiftProtobuf dependency: decode with your own message types, e.g. `try Order(serializedBytes: $0).jsonString()`.
public struct DariProtobufDecoder: Sendable {
    /// Returning nil reports the decoder as unavailable; throwing reports a failed decode.
    public typealias Decode = @Sendable (Data) throws -> String?

    private struct Key: Hashable {
        let handlerName: String
        let direction: MessageDirection
        let part: Part
    }

    enum Part { case request, response }

    private var decoders: [Key: Decode] = [:]

    public init() {}

    /// A Web → App request and the app's response.
    public func webToApp(_ handlerName: String, request: Decode? = nil, response: Decode? = nil) -> DariProtobufDecoder {
        registering(handlerName, .webToApp, request, response)
    }

    /// An App → Web request and the web's response.
    public func appToWeb(_ handlerName: String, request: Decode? = nil, response: Decode? = nil) -> DariProtobufDecoder {
        registering(handlerName, .appToWeb, request, response)
    }

    func decoder(handlerName: String, direction: MessageDirection, part: Part) -> Decode? {
        decoders[Key(handlerName: handlerName, direction: direction, part: part)]
    }

    private func registering(_ handlerName: String, _ direction: MessageDirection, _ request: Decode?, _ response: Decode?) -> DariProtobufDecoder {
        var copy = self
        for (part, decode) in [(Part.request, request), (.response, response)] {
            guard let decode else { continue }
            let key = Key(handlerName: handlerName, direction: direction, part: part)
            // Fails fast like Android's registry.
            precondition(copy.decoders[key] == nil, "Duplicate protobuf decoder for \(handlerName) (\(direction.rawValue), \(part))")
            copy.decoders[key] = decode
        }
        return copy
    }
}

/// Mirrors `MessagePayloadMetadata`; raw values match the Kotlin enums the desktop reads.
package struct PayloadMetadata: Equatable {
    package enum DecodeStatus: String {
        case decoded = "DECODED"
        case decoderUnavailable = "DECODER_UNAVAILABLE"
        case failed = "FAILED"
    }

    package struct RawPreview: Equatable {
        package let base64: String
        package let sizeBytes: Int
        package let truncated: Bool
    }

    package let originalSizeBytes: Int
    package let decodeStatus: DecodeStatus
    package var rawPreview: RawPreview?

    var wire: WirePayloadMetadata {
        WirePayloadMetadata(
            contentType: "PROTOBUF",
            originalSizeBytes: originalSizeBytes,
            decodeStatus: decodeStatus.rawValue,
            rawPreviewBase64: rawPreview?.base64,
            rawPreviewSizeBytes: rawPreview?.sizeBytes,
            rawPreviewTruncated: rawPreview?.truncated
        )
    }
}

/// Port of `ProtobufPayloadRenderer`. Runs on the caller's thread, outside Dari's locks.
enum ProtobufRenderer {
    static let rawPreviewMaxBytes = 4 * 1024

    static func render(_ payload: Data, decode: DariProtobufDecoder.Decode?, maxContentLength: Int) -> (text: String, truncated: Bool, metadata: PayloadMetadata) {
        let text: String
        let status: PayloadMetadata.DecodeStatus
        do {
            if let decoded = try decode?(payload) {
                (text, status) = (decoded, .decoded)
            } else {
                (text, status) = ("(protobuf decoder unavailable)", .decoderUnavailable)
            }
        } catch {
            (text, status) = ("(protobuf decoding failed)", .failed)
        }
        let (truncatedText, truncated) = MessageRecord.truncate(text, maxLength: maxContentLength)
        let preview = payload.prefix(rawPreviewMaxBytes)
        let metadata = PayloadMetadata(
            originalSizeBytes: payload.count,
            decodeStatus: status,
            rawPreview: .init(base64: Data(preview).base64EncodedString(), sizeBytes: preview.count, truncated: preview.count < payload.count)
        )
        return (truncatedText ?? "", truncated, metadata)
    }
}
