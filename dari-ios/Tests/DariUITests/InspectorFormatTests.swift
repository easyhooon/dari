@testable import Dari
@testable import DariUI
import XCTest

final class InspectorFormatTests: XCTestCase {
    private let utc: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()

    func testPrettyJSONKeepsKeyOrderAndStrings() {
        XCTAssertEqual(
            InspectorFormat.prettyJSON(#"{"z":1,"a":{"s":"x,{y}:\"q\""},"l":[1,2],"e":{},"n":[]}"#),
            """
            {
              "z": 1,
              "a": {
                "s": "x,{y}:\\"q\\""
              },
              "l": [
                1,
                2
              ],
              "e": {},
              "n": []
            }
            """
        )
    }

    func testPrettyJSONLeavesOtherTextAlone() {
        XCTAssertEqual(InspectorFormat.prettyJSON("fire and forget"), "fire and forget")
        XCTAssertEqual(InspectorFormat.prettyJSON(#"{"a":1"#), #"{"a":1"#)
        XCTAssertEqual(InspectorFormat.prettyJSON("]["), "][")
        // Bracketed text that isn't JSON must stay as recorded.
        XCTAssertEqual(InspectorFormat.prettyJSON("[object Object]"), "[object Object]")
        XCTAssertEqual(InspectorFormat.prettyJSON("[INFO] user logged in: ok"), "[INFO] user logged in: ok")
        XCTAssertEqual(InspectorFormat.prettyJSON(#"{"a":1} // note"#), #"{"a":1} // note"#)
    }

    func testHexMatchesAndroid() {
        let preview = PayloadMetadata.RawPreview(base64: Data(0..<18).base64EncodedString(), sizeBytes: 18, truncated: false)
        XCTAssertEqual(
            InspectorFormat.hex(preview),
            "00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F\n10 11"
        )
    }

    func testSingleMessageText() {
        let text = InspectorFormat.text(record(), dateFormatter: utc)
        XCTAssertTrue(text.hasPrefix("Handler: getUser\nDisplay name: Get user\nDirection: Web → App\nStatus: SUCCESS\nTag: main\n"))
        XCTAssertTrue(text.contains("Request time: 00:00:01\nResponse time: 00:00:01\nDuration: 250 ms"))
        XCTAssertTrue(text.contains("Request size: 4 B\nResponse size: 15 B\nTotal size: 19 B"))
        XCTAssertTrue(text.contains("Request content type: PROTOBUF\nRequest decode status: DECODED"))
        XCTAssertTrue(text.contains("---------- Request (Raw / Hex) ----------\nCaptured: 4 of 4 bytes\n\n0A 02 68 69"))
        XCTAssertTrue(text.contains("---------- Response (Decoded) ----------\n\n{\n  \"name\": \"Dari\"\n}"))
    }

    func testJSONExportUsesSnakeCaseAndIOSMeta() throws {
        let app = AppInfo(
            applicationId: "com.example", appName: "Example", appVersion: "1.0", dariVersion: "3.5.0",
            deviceModel: "iPhone17,1", sdkInt: 0, environment: ["server": "dev"], platform: "ios", osVersion: "18.2.0"
        )
        let json = InspectorFormat.export([record()], app: app, format: .json, exportedAt: Date(timeIntervalSince1970: 2))
        // Key order and explicit nulls follow the Kotlin exporters.
        XCTAssertTrue(json.hasPrefix("{\n  \"format_version\": 2,\n  \"meta\": {\n    \"application_id\": \"com.example\","))
        XCTAssertTrue(json.contains("\"page_url\": null,"))
        XCTAssertTrue(json.contains("\"response_content_type\": null,"))
        XCTAssertLessThan(try XCTUnwrap(json.range(of: "\"meta\"")).lowerBound, try XCTUnwrap(json.range(of: "\"messages\"")).lowerBound)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any])
        XCTAssertEqual(object["format_version"] as? Int, 2)
        let meta = try XCTUnwrap(object["meta"] as? [String: Any])
        XCTAssertEqual(meta["platform"] as? String, "ios")
        XCTAssertEqual(meta["os_version"] as? String, "18.2.0")
        XCTAssertEqual(meta["exported_at"] as? Int, 2_000)
        let message = try XCTUnwrap((object["messages"] as? [[String: Any]])?.first)
        XCTAssertEqual(message["handler_name"] as? String, "getUser")
        XCTAssertEqual(message["duration_ms"] as? Int, 250)
        XCTAssertEqual(message["request_decode_status"] as? String, "DECODED")
        XCTAssertTrue(message["response_content_type"] is NSNull)

        let text = InspectorFormat.export([record()], app: app, format: .text, exportedAt: Date(timeIntervalSince1970: 2), dateFormatter: utc)
        XCTAssertTrue(text.hasPrefix("App: Example 1.0 (com.example)\nDevice: iPhone17,1, iOS 18.2.0\nDari: 3.5.0\nEnvironment: server=dev\nExported: 00:00:02\n"))
    }

    private func record() -> MessageRecord {
        MessageRecord(
            id: 1, requestId: "r", handlerName: "getUser", displayName: "Get user", direction: .webToApp, tag: "main",
            requestData: #"{"value":"hi"}"#, responseData: #"{"name":"Dari"}"#,
            requestPayloadMetadata: PayloadMetadata(
                originalSizeBytes: 4, decodeStatus: .decoded,
                rawPreview: .init(base64: "CgJoaQ==", sizeBytes: 4, truncated: false)
            ),
            status: .success, requestTimestamp: 1_000, responseTimestamp: 1_250, pageUrl: nil
        )
    }
}
