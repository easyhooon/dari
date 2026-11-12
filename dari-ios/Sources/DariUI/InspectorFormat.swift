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
        // Parse first, like Android and the desktop app, so `[INFO] …` or `[object Object]` stay as recorded.
        guard (try? JSONSerialization.jsonObject(with: Data(text.utf8))) != nil else { return text }
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
            let exported = Int64((exportedAt.timeIntervalSince1970 * 1000).rounded())
            let document: [(String, Any?)] = [
                ("format_version", 2),
                ("meta", meta(app, exported)),
                ("messages", records.map(exportable)),
            ]
            return OrderedJSON.write(document)
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

// Field names and order follow `ExportMeta` / `ExportableMessage` in :dari and the desktop app's iOS variant.
// Nil values are written as null, as Kotlin's `encodeDefaults = true` does.
extension InspectorFormat {
    static func meta(_ app: AppInfo, _ exportedAt: Int64) -> [(String, Any?)] {
        [
            ("application_id", app.applicationId),
            ("app_name", app.appName),
            ("app_version", app.appVersion),
            ("dari_version", app.dariVersion),
            ("device_model", app.deviceModel),
            ("sdk_int", app.sdkInt),
            ("exported_at", exportedAt),
            ("environment", app.environment.map { env in env.sorted { $0.key < $1.key }.map { ($0.key, $0.value as Any?) } }),
            ("platform", app.platform),
            ("os_version", app.osVersion),
        ]
    }

    static func exportable(_ record: MessageRecord) -> [(String, Any?)] {
        let request = record.requestPayloadMetadata
        let response = record.responsePayloadMetadata
        return [
            ("id", record.id),
            ("request_id", record.requestId),
            ("handler_name", record.handlerName),
            ("display_name", record.displayName),
            ("direction", record.direction.rawValue),
            ("tag", record.tag),
            ("page_url", record.pageUrl),
            ("request_data", record.requestData),
            ("response_data", record.responseData),
            ("request_data_truncated", record.requestDataTruncated),
            ("response_data_truncated", record.responseDataTruncated),
            ("request_content_type", request.map { _ in "PROTOBUF" }),
            ("request_original_size_bytes", request?.originalSizeBytes),
            ("request_decode_status", request?.decodeStatus.rawValue),
            ("request_raw_preview_base64", request?.rawPreview?.base64),
            ("request_raw_preview_size_bytes", request?.rawPreview?.sizeBytes),
            ("request_raw_preview_truncated", request?.rawPreview?.truncated),
            ("response_content_type", response.map { _ in "PROTOBUF" }),
            ("response_original_size_bytes", response?.originalSizeBytes),
            ("response_decode_status", response?.decodeStatus.rawValue),
            ("response_raw_preview_base64", response?.rawPreview?.base64),
            ("response_raw_preview_size_bytes", response?.rawPreview?.sizeBytes),
            ("response_raw_preview_truncated", response?.rawPreview?.truncated),
            ("status", record.status.rawValue),
            ("request_timestamp", record.requestTimestamp),
            ("response_timestamp", record.responseTimestamp),
            ("duration_ms", duration(record)),
        ]
    }
}

/// Pretty JSON with keys in the given order, two-space indent like the Kotlin exporters.
enum OrderedJSON {
    static func write(_ object: [(String, Any?)]) -> String {
        var out = ""
        append(object, to: &out, depth: 0)
        return out
    }

    private static func append(_ value: Any?, to out: inout String, depth: Int) {
        let indent = String(repeating: "  ", count: depth + 1)
        let closing = String(repeating: "  ", count: depth)
        switch value {
        case nil:
            out += "null"
        case let object as [(String, Any?)]:
            guard !object.isEmpty else { out += "{}"; return }
            out += "{\n"
            for (index, (key, item)) in object.enumerated() {
                out += indent + string(key) + ": "
                append(item, to: &out, depth: depth + 1)
                out += index == object.count - 1 ? "\n" : ",\n"
            }
            out += closing + "}"
        case let array as [[(String, Any?)]]:
            guard !array.isEmpty else { out += "[]"; return }
            out += "[\n"
            for (index, item) in array.enumerated() {
                out += indent
                append(item, to: &out, depth: depth + 1)
                out += index == array.count - 1 ? "\n" : ",\n"
            }
            out += closing + "]"
        case let text as String:
            out += string(text)
        case let flag as Bool:
            out += flag ? "true" : "false"
        case let number as Int:
            out += String(number)
        case let number as Int64:
            out += String(number)
        case let .some(other):
            out += string(String(describing: other))
        }
    }

    private static func string(_ text: String) -> String {
        let data = (try? JSONSerialization.data(withJSONObject: text, options: [.fragmentsAllowed, .withoutEscapingSlashes])) ?? Data("\"\"".utf8)
        return String(decoding: data, as: UTF8.self)
    }
}
