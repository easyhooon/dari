@testable import Dari
import XCTest

final class ProtobufTests: XCTestCase {
    private struct Failure: Error {}

    // google.protobuf.StringValue("hi"): field 1, length 2.
    private let hi = Data([0x0A, 0x02, 0x68, 0x69])
    private let dari = Dari()

    override func setUpWithError() throws {
        try dari.start(DariConfiguration(ports: 38_700...38_707))
    }

    override func tearDown() {
        dari.stop()
    }

    func testRecordsDecodedTextWithMetadata() throws {
        let decoder = DariProtobufDecoder()
            .webToApp("greet", request: { String(decoding: $0.dropFirst(2), as: UTF8.self) }, response: { _ in throw Failure() })
        let bridge = dari.interceptor(tag: "t", protobufDecoder: decoder)
        bridge.onWebToAppProtobufRequest(handlerName: "greet", requestId: "1", requestData: hi)
        bridge.onWebToAppProtobufResponse(handlerName: "greet", requestId: "1", responseData: Data([1, 2]), isSuccess: true)

        let record = try XCTUnwrap(dari.records.first)
        XCTAssertEqual(record.requestData, "hi")
        XCTAssertEqual(record.requestPayloadMetadata?.decodeStatus, .decoded)
        XCTAssertEqual(record.requestPayloadMetadata?.rawPreview, .init(base64: "CgJoaQ==", sizeBytes: 4, truncated: false))
        XCTAssertEqual(record.responseData, "(protobuf decoding failed)")
        XCTAssertEqual(record.responsePayloadMetadata?.decodeStatus, .failed)
        XCTAssertEqual(record.status, .success)
        // Sizes report the protobuf bytes, not the decoded text.
        XCTAssertEqual(record.summary.requestSizeBytes, 4)
        XCTAssertEqual(record.summary.responseSizeBytes, 2)
        XCTAssertEqual(record.detail.requestPayloadMetadata?.contentType, "PROTOBUF")
    }

    func testUnregisteredHandlerIsUnavailable() throws {
        dari.interceptor().onAppToWebProtobufRequest(handlerName: "other", requestId: nil, data: hi)
        let record = try XCTUnwrap(dari.records.first)
        XCTAssertEqual(record.requestData, "(protobuf decoder unavailable)")
        XCTAssertEqual(record.requestPayloadMetadata?.decodeStatus, .decoderUnavailable)
    }

    func testAppToWebResponseUsesTheRequestsHandlerName() throws {
        let decoder = DariProtobufDecoder().appToWeb("push", response: { _ in "decoded" })
        let bridge = dari.interceptor(protobufDecoder: decoder)
        bridge.onAppToWebProtobufRequest(handlerName: "push", requestId: "1", data: hi)
        bridge.onAppToWebProtobufResponse(requestId: "1", isSuccess: false, responseData: hi)
        let record = try XCTUnwrap(dari.records.first)
        XCTAssertEqual(record.responseData, "decoded")
        XCTAssertEqual(record.status, .error)
    }

    func testDecoderThatRecordsDoesNotDeadlock() throws {
        let log = dari.interceptor(tag: "log")
        let decoder = DariProtobufDecoder().webToApp("greet", request: { data in
            log.onWebToAppRequest(handlerName: "decoding", requestId: nil, requestData: "\(data.count)")
            return "ok"
        })
        dari.interceptor(protobufDecoder: decoder).onWebToAppProtobufRequest(handlerName: "greet", requestId: "1", requestData: hi)
        XCTAssertEqual(dari.records.map(\.handlerName), ["decoding", "greet"])
    }

    func testRawPreviewIsCappedAt4KB() {
        let rendered = ProtobufRenderer.render(Data(count: 5_000), decode: nil, maxContentLength: 100)
        XCTAssertEqual(rendered.metadata.rawPreview?.sizeBytes, 4_096)
        XCTAssertEqual(rendered.metadata.rawPreview?.truncated, true)
        XCTAssertEqual(rendered.metadata.originalSizeBytes, 5_000)
    }

    func testMaskingDropsRawPreview() throws {
        dari.interceptor().onWebToAppProtobufRequest(handlerName: "greet", requestId: nil, requestData: hi)
        let masked = PayloadMasker.mask(try XCTUnwrap(dari.records.first))
        XCTAssertNotNil(masked.requestPayloadMetadata)
        XCTAssertNil(masked.requestPayloadMetadata?.rawPreview)
        XCTAssertNil(masked.detail.requestPayloadMetadata?.rawPreviewBase64)
    }

    func testRedactorDropsRawPreview() throws {
        dari.interceptor().onWebToAppProtobufRequest(handlerName: "greet", requestId: nil, requestData: hi)
        let record = try XCTUnwrap(dari.records.first)
        XCTAssertNotNil(record.requestPayloadMetadata?.rawPreview)
        XCTAssertNil(record.redacted { $0 }.requestPayloadMetadata?.rawPreview)
        XCTAssertNil(record.redacted { _ in throw Failure() }.requestPayloadMetadata?.rawPreview)
    }
}
