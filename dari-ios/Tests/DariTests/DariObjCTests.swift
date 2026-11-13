@testable import Dari
import Foundation
import XCTest

final class DariObjCTests: XCTestCase {
    override func tearDown() {
        Dari.shared.stop()
        super.tearDown()
    }

    // dari-kmp reaches these classes by name and selector only, so a rename here breaks it without a compile error.
    func testRuntimeNamesAndSelectorsMatchDariKmp() throws {
        let defURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("dari-kmp/src/nativeInterop/cinterop/dariObjC.def")
        let def = try String(contentsOf: defURL, encoding: .utf8)
        let selectors = try NSRegularExpression(pattern: #"@selector\(([^)]+)\)"#)
            .matches(in: def, range: NSRange(def.startIndex..., in: def))
            .compactMap { Range($0.range(at: 1), in: def).map { String(def[$0]) } }
        XCTAssertEqual(selectors.count, 6)

        let factory = try XCTUnwrap(NSClassFromString("DariObjC") as? NSObject.Type)
        let interceptor = try XCTUnwrap(NSClassFromString("DariObjCInterceptor") as? NSObject.Type)
        for name in selectors {
            let selector = NSSelectorFromString(name)
            let found = factory.responds(to: selector) || interceptor.instancesRespond(to: selector)
            XCTAssertTrue(found, "\(name) is declared in dariObjC.def but missing from DariObjC")
        }
    }

    func testForwardsToTheSwiftInterceptor() throws {
        try Dari.shared.start(DariConfiguration(ports: 38_690...38_699))
        let interceptor = DariObjC.interceptor(tag: "kmp")
        interceptor.onPageUrlChanged("https://example.com/page?token=1")
        interceptor.onWebToAppRequest(handlerName: "getUser", displayName: "Get user", requestId: "1", requestData: "{}", fireAndForget: nil)
        interceptor.onWebToAppResponse(handlerName: "getUser", requestId: "1", responseData: #"{"ok":true}"#, isSuccess: true)
        interceptor.onAppToWebRequest(handlerName: "log", displayName: nil, requestId: nil, data: "hi", fireAndForget: NSNumber(value: true))

        let records = Dari.shared.records
        XCTAssertEqual(records.map(\.handlerName), ["getUser", "log"])
        XCTAssertEqual(records.map(\.status), [.success, .success])
        XCTAssertEqual(records.first?.displayName, "Get user")
        XCTAssertEqual(records.first?.responseData, #"{"ok":true}"#)
        XCTAssertEqual(records.first?.pageUrl, "https://example.com/page")
        XCTAssertEqual(records.first?.tag, "kmp")
    }
}
