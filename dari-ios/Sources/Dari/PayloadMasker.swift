import Foundation

/// Port of Android's `PayloadMasker`, applied per desktop connection when masking is on.
/// Fails closed: a payload that can't be processed is replaced, not shown raw.
enum PayloadMasker {
    static let mask = "***"
    static let redactionFailed = "[dari: payload hidden, masking failed]"

    // Matched against keys lowercased with separators removed, e.g. "Access-Token" -> "accesstoken".
    private static let sensitiveKeyParts = [
        "token", "password", "passwd", "secret", "authorization", "cookie", "apikey", "sessionid", "cardnumber",
    ]
    private static let sensitiveKeys: Set<String> = ["cvv", "cvc", "ssn", "otp", "pin"]

    private static let keyValue = try! NSRegularExpression(
        pattern: #"(?i)\b([\w.\-]*(?:token|password|passwd|secret|authorization|cookie|api[_\-]?key|session[_\-]?id|card[_\-]?number)[\w.\-]*)(["']?\s*[:=]\s*)("(?:[^"\\]|\\.)*"|'[^']*'|[^\s,;&}\]]+)"#
    )
    private static let authScheme = try! NSRegularExpression(pattern: #"(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/\-]+=*"#)

    static func mask(_ record: MessageRecord) -> MessageRecord {
        var masked = record
        masked.requestData = maskText(record.requestData)
        masked.responseData = maskText(record.responseData)
        return masked
    }

    static func maskText(_ text: String?) -> String? {
        guard let text else { return nil }
        let masked = maskJSON(text) ?? maskKeyValues(text)
        guard let masked else { return redactionFailed }
        let range = NSRange(masked.startIndex..., in: masked)
        return authScheme.stringByReplacingMatches(in: masked, range: range, withTemplate: "$1 \(mask)")
    }

    static func isSensitiveKey(_ key: String) -> Bool {
        let normalized = String(key.lowercased().filter { $0.isLetter || $0.isNumber })
        return sensitiveKeys.contains(normalized) || sensitiveKeyParts.contains { normalized.contains($0) }
    }

    /// Nil when [text] isn't a JSON object or array. Key order follows Foundation, not the source text.
    private static func maskJSON(_ text: String) -> String? {
        let trimmed = text.drop { $0.isWhitespace }
        guard trimmed.hasPrefix("{") || trimmed.hasPrefix("["),
              let object = try? JSONSerialization.jsonObject(with: Data(text.utf8), options: [.fragmentsAllowed]),
              let data = try? JSONSerialization.data(withJSONObject: maskValue(object), options: [.withoutEscapingSlashes])
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func maskValue(_ value: Any) -> Any {
        switch value {
        case let object as [String: Any]:
            return Dictionary(uniqueKeysWithValues: object.map { key, value in
                (key, isSensitiveKey(key) ? mask as Any : maskValue(value))
            })
        case let array as [Any]:
            return array.map(maskValue)
        case let string as String:
            // Bridges often nest stringified JSON; mask inside it too.
            return maskText(string) ?? redactionFailed
        default:
            return value
        }
    }

    private static func maskKeyValues(_ text: String) -> String? {
        var result = ""
        var last = text.startIndex
        for match in keyValue.matches(in: text, range: NSRange(text.startIndex..., in: text)) {
            guard let whole = Range(match.range, in: text),
                  let key = Range(match.range(at: 1), in: text),
                  let separator = Range(match.range(at: 2), in: text),
                  let value = Range(match.range(at: 3), in: text)
            else { return nil }
            let original = text[value]
            let replacement = original.hasPrefix("\"") ? "\"\(mask)\"" : original.hasPrefix("'") ? "'\(mask)'" : mask
            result += text[last..<whole.lowerBound] + text[key] + text[separator] + replacement
            last = whole.upperBound
        }
        return result + text[last...]
    }
}
