import Dari
import Foundation

/// Text the inspector shows and shares. Matches the Android exporter and the desktop app's iOS variant.
enum InspectorFormat {
    enum ExportFormat: String, CaseIterable, Identifiable {
        case text, json

        var id: String { rawValue }
        var fileExtension: String { self == .text ? "txt" : "json" }
    }

    static func defaultDateFormatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE MMM dd HH:mm:ss zzz yyyy"
        return formatter
    }

    static func direction(_ direction: MessageDirection) -> String {
        direction == .webToApp ? "Web → App" : "App → Web"
    }

    static func duration(_ record: MessageRecord) -> Int64? {
        record.responseTimestamp.map { $0 - record.requestTimestamp }
    }

    static func requestSize(_ record: MessageRecord) -> Int {
        record.requestPayloadMetadata?.originalSizeBytes ?? record.requestData?.utf8.count ?? 0
    }

    static func responseSize(_ record: MessageRecord) -> Int {
        record.responsePayloadMetadata?.originalSizeBytes ?? record.responseData?.utf8.count ?? 0
    }

    static func size(_ bytes: Int) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return String(format: "%.1f KB", Double(bytes) / 1024) }
        return String(format: "%.1f MB", Double(bytes) / (1024 * 1024))
    }

    /// Indents JSON without reordering keys, unlike `JSONSerialization`. Anything else comes back unchanged.
    static func prettyJSON(_ text: String) -> String {
        let chars = Array(text.trimmingCharacters(in: .whitespacesAndNewlines))
        guard let first = chars.first, first == "{" || first == "[" else { return text }
        var out = ""
        var depth = 0
        var inString = false
        var escaped = false
        var index = 0
        func newline() { out += "\n" + String(repeating: "  ", count: depth) }
        func nextNonSpace(after i: Int) -> Character? {
            chars[(i + 1)...].first { !$0.isWhitespace }
        }
        while index < chars.count {
            let char = chars[index]
            defer { index += 1 }
            if inString {
                out.append(char)
                if escaped { escaped = false } else if char == "\\" { escaped = true } else if char == "\"" { inString = false }
                continue
            }
            switch char {
            case "\"":
                inString = true
                out.append(char)
            case "{", "[":
                let close: Character = char == "{" ? "}" : "]"
                if nextNonSpace(after: index) == close {
                    out += String([char, close])
                    index = chars[(index + 1)...].firstIndex(of: close) ?? index
                } else {
                    depth += 1
                    out.append(char)
                    newline()
                }
            case "}", "]":
                depth -= 1
                guard depth >= 0 else { return text }
                newline()
                out.append(char)
            case ",":
                out.append(char)
                newline()
            case ":":
                out += ": "
            case _ where char.isWhitespace:
                break
            default:
                out.append(char)
            }
        }
        return depth == 0 && !inString ? out : text
    }

    /// `0A 04 44 61 …`, 16 bytes per line, like Android's `RawPayloadFormatter`.
    static func hex(_ preview: PayloadMetadata.RawPreview) -> String {
        guard let bytes = Data(base64Encoded: preview.base64) else { return "(raw preview unavailable)" }
        if bytes.isEmpty { return "(empty)" }
        return stride(from: 0, to: bytes.count, by: 16).map { start in
            bytes[bytes.index(bytes.startIndex, offsetBy: start)..<bytes.index(bytes.startIndex, offsetBy: min(start + 16, bytes.count))]
                .map { String(format: "%02X", $0) }
                .joined(separator: " ")
        }.joined(separator: "\n")
    }

    /// One message as readable text, the format Android and the desktop app share to chat or email.
    static func text(_ record: MessageRecord, dateFormatter: DateFormatter = defaultDateFormatter()) -> String {
        func date(_ millis: Int64) -> String { dateFormatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000)) }
        let requestSize = requestSize(record)
        let responseSize = responseSize(record)
        var lines = ["Handler: \(record.handlerName)"]
        if let name = record.displayName, !name.isEmpty { lines.append("Display name: \(name)") }
        lines += [
            "Direction: \(direction(record.direction))",
            "Status: \(record.status.rawValue)",
            "Tag: \(record.tag ?? "-")",
        ]
        if let page = record.pageUrl { lines.append("Page: \(page)") }
        lines += ["Request ID: \(record.requestId ?? "-")", "", "Request time: \(date(record.requestTimestamp))"]
        if let response = record.responseTimestamp { lines.append("Response time: \(date(response))") }
        if let duration = duration(record) { lines.append("Duration: \(duration) ms") }
        lines += [
            "",
            "Request size: \(size(requestSize))\(record.requestDataTruncated ? " (truncated)" : "")",
            "Response size: \(size(responseSize))\(record.responseDataTruncated ? " (truncated)" : "")",
            "Total size: \(size(requestSize + responseSize))",
        ]
        for (label, metadata) in [("Request", record.requestPayloadMetadata), ("Response", record.responsePayloadMetadata)] {
            guard let metadata else { continue }
            lines += ["\(label) content type: PROTOBUF", "\(label) decode status: \(metadata.decodeStatus.rawValue)"]
        }
        for (label, data, metadata) in [
            ("Request", record.requestData, record.requestPayloadMetadata),
            ("Response", record.responseData, record.responsePayloadMetadata),
        ] {
            lines += ["", "---------- \(label) (Decoded) ----------", "", data.map(prettyJSON) ?? "(empty)"]
            if let metadata, let preview = metadata.rawPreview {
                lines += [
                    "",
                    "---------- \(label) (Raw / Hex) ----------",
                    "Captured: \(preview.sizeBytes) of \(metadata.originalSizeBytes) bytes\(preview.truncated ? " (truncated)" : "")",
                    "",
                    hex(preview),
                    "",
                    "---------- \(label) (Raw / Base64) ----------",
                    "",
                    preview.base64.isEmpty ? "(empty)" : preview.base64,
                ]
            }
        }
        return lines.joined(separator: "\n") + "\n"
    }

    /// Every message with a header naming the app, oldest first like the Android export.
    static func export(
        _ records: [MessageRecord],
        app: AppInfo,
        format: ExportFormat,
        exportedAt: Date = Date(),
        dateFormatter: DateFormatter = defaultDateFormatter()
    ) -> String {
        switch format {
        case .text:
            let separator = "\n" + String(repeating: "=", count: 60) + "\n\n"
            return header(app, exportedAt, dateFormatter) + separator
                + records.map { text($0, dateFormatter: dateFormatter) }.joined(separator: separator)
        case .json:
            let document = ExportDocument(
                meta: ExportMeta(app: app, exportedAt: Int64((exportedAt.timeIntervalSince1970 * 1000).rounded())),
                messages: records.map(ExportMessage.init)
            )
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
            return (try? encoder.encode(document)).map { String(decoding: $0, as: UTF8.self) } ?? "{}"
        }
    }

    static func header(_ app: AppInfo, _ exportedAt: Date, _ dateFormatter: DateFormatter) -> String {
        let name = [app.appName, app.appVersion].filter { !$0.isEmpty }.joined(separator: " ")
        var lines = [
            "App: \(name) (\(app.applicationId))",
            "Device: \(app.deviceModel), iOS \(app.osVersion ?? "")".trimmingCharacters(in: .whitespaces),
            "Dari: \(app.dariVersion)",
        ]
        if let environment = app.environment, !environment.isEmpty {
            lines.append("Environment: " + environment.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: ", "))
        }
        lines.append("Exported: \(dateFormatter.string(from: exportedAt))")
        return lines.joined(separator: "\n") + "\n"
    }

    static func fileName(_ format: ExportFormat, now: Date = Date()) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        return "dari_export_\(formatter.string(from: now)).\(format.fileExtension)"
    }
}

// Field names follow `ExportDocument` in :dari and the desktop app's iOS variant.
private struct ExportDocument: Encodable {
    let formatVersion = 2
    let meta: ExportMeta
    let messages: [ExportMessage]

    enum CodingKeys: String, CodingKey {
        case formatVersion = "format_version", meta, messages
    }
}

private struct ExportMeta: Encodable {
    let applicationId, appName, appVersion, dariVersion, deviceModel: String
    let sdkInt: Int
    let exportedAt: Int64
    let environment: [String: String]?
    let platform: String?
    let osVersion: String?

    init(app: AppInfo, exportedAt: Int64) {
        applicationId = app.applicationId
        appName = app.appName
        appVersion = app.appVersion
        dariVersion = app.dariVersion
        deviceModel = app.deviceModel
        sdkInt = app.sdkInt
        self.exportedAt = exportedAt
        environment = app.environment
        platform = app.platform
        osVersion = app.osVersion
    }

    enum CodingKeys: String, CodingKey {
        case applicationId = "application_id", appName = "app_name", appVersion = "app_version"
        case dariVersion = "dari_version", deviceModel = "device_model", sdkInt = "sdk_int"
        case exportedAt = "exported_at", environment, platform, osVersion = "os_version"
    }
}

private struct ExportMessage: Encodable {
    let id: Int64
    let requestId: String?
    let handlerName: String
    let displayName: String?
    let direction: String
    let tag: String?
    let pageUrl: String?
    let requestData: String?
    let responseData: String?
    let requestDataTruncated: Bool
    let responseDataTruncated: Bool
    let requestContentType: String?
    let requestOriginalSizeBytes: Int?
    let requestDecodeStatus: String?
    let requestRawPreviewBase64: String?
    let requestRawPreviewSizeBytes: Int?
    let requestRawPreviewTruncated: Bool?
    let responseContentType: String?
    let responseOriginalSizeBytes: Int?
    let responseDecodeStatus: String?
    let responseRawPreviewBase64: String?
    let responseRawPreviewSizeBytes: Int?
    let responseRawPreviewTruncated: Bool?
    let status: String
    let requestTimestamp: Int64
    let responseTimestamp: Int64?
    let durationMs: Int64?

    init(_ record: MessageRecord) {
        id = record.id
        requestId = record.requestId
        handlerName = record.handlerName
        displayName = record.displayName
        direction = record.direction.rawValue
        tag = record.tag
        pageUrl = record.pageUrl
        requestData = record.requestData
        responseData = record.responseData
        requestDataTruncated = record.requestDataTruncated
        responseDataTruncated = record.responseDataTruncated
        let request = record.requestPayloadMetadata
        requestContentType = request.map { _ in "PROTOBUF" }
        requestOriginalSizeBytes = request?.originalSizeBytes
        requestDecodeStatus = request?.decodeStatus.rawValue
        requestRawPreviewBase64 = request?.rawPreview?.base64
        requestRawPreviewSizeBytes = request?.rawPreview?.sizeBytes
        requestRawPreviewTruncated = request?.rawPreview?.truncated
        let response = record.responsePayloadMetadata
        responseContentType = response.map { _ in "PROTOBUF" }
        responseOriginalSizeBytes = response?.originalSizeBytes
        responseDecodeStatus = response?.decodeStatus.rawValue
        responseRawPreviewBase64 = response?.rawPreview?.base64
        responseRawPreviewSizeBytes = response?.rawPreview?.sizeBytes
        responseRawPreviewTruncated = response?.rawPreview?.truncated
        status = record.status.rawValue
        requestTimestamp = record.requestTimestamp
        responseTimestamp = record.responseTimestamp
        durationMs = InspectorFormat.duration(record)
    }

    enum CodingKeys: String, CodingKey {
        case id, direction, tag, status
        case requestId = "request_id", handlerName = "handler_name", displayName = "display_name", pageUrl = "page_url"
        case requestData = "request_data", responseData = "response_data"
        case requestDataTruncated = "request_data_truncated", responseDataTruncated = "response_data_truncated"
        case requestContentType = "request_content_type", requestOriginalSizeBytes = "request_original_size_bytes"
        case requestDecodeStatus = "request_decode_status", requestRawPreviewBase64 = "request_raw_preview_base64"
        case requestRawPreviewSizeBytes = "request_raw_preview_size_bytes", requestRawPreviewTruncated = "request_raw_preview_truncated"
        case responseContentType = "response_content_type", responseOriginalSizeBytes = "response_original_size_bytes"
        case responseDecodeStatus = "response_decode_status", responseRawPreviewBase64 = "response_raw_preview_base64"
        case responseRawPreviewSizeBytes = "response_raw_preview_size_bytes", responseRawPreviewTruncated = "response_raw_preview_truncated"
        case requestTimestamp = "request_timestamp", responseTimestamp = "response_timestamp", durationMs = "duration_ms"
    }
}
