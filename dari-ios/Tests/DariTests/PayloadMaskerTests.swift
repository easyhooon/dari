@testable import Dari
import XCTest

final class PayloadMaskerTests: XCTestCase {
    func testMasksSensitiveJsonKeysIncludingNestedJsonStrings() throws {
        let masked = try XCTUnwrap(PayloadMasker.maskText(#"{"accessToken":"a","user":{"pin":"1"},"inner":"{\"password\":\"p\"}","id":7}"#))
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(masked.utf8)) as? [String: Any])
        XCTAssertEqual(object["accessToken"] as? String, "***")
        XCTAssertEqual((object["user"] as? [String: Any])?["pin"] as? String, "***")
        XCTAssertEqual(object["inner"] as? String, #"{"password":"***"}"#)
        XCTAssertEqual(object["id"] as? Int, 7)
    }

    func testMasksKeyValuesAndAuthSchemes() {
        XCTAssertEqual(PayloadMasker.maskText("token=abc&id=1"), "token=***&id=1")
        XCTAssertEqual(PayloadMasker.maskText(#"session_id: "x""#), #"session_id: "***""#)
        XCTAssertEqual(PayloadMasker.maskText("Bearer abc.def"), "Bearer ***")
        XCTAssertEqual(PayloadMasker.maskText("plain"), "plain")
    }

    func testMasksValueOfSensitiveNameValuePair() throws {
        let masked = try XCTUnwrap(PayloadMasker.maskText(
            #"[{"name":"Authorization","value":"xyz"},{"Key":"X-Api-Key","Value":{"v":"k"}},{"name":"Accept","value":"json"},{"name":7,"value":"keep"},{"NAME":"id","key":"Cookie","VALUE":"c"}]"#
        ))
        let pairs = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(masked.utf8)) as? [[String: Any]])
        XCTAssertEqual(pairs[0]["value"] as? String, "***")
        XCTAssertEqual(pairs[1]["Value"] as? String, "***")
        XCTAssertEqual(pairs[2]["value"] as? String, "json")
        XCTAssertEqual(pairs[3]["value"] as? String, "keep")
        XCTAssertEqual(pairs[4]["VALUE"] as? String, "***")
    }
}
