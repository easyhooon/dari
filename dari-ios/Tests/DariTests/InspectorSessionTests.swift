@testable import Dari
import XCTest

final class InspectorSessionTests: XCTestCase {
    private let app = AppInfo(
        applicationId: "a", appName: "A", appVersion: "1", dariVersion: "3", deviceModel: "m", sdkInt: 0
    )

    func testDisconnectWhileRecordingDoesNotDeadlock() throws {
        let store = MessageStore(maxEntries: 100)
        let (client, finished) = try startSession(store)
        try client.write(ClientHello(protocolVersion: 1, clientName: "t", clientVersion: "t"))
        _ = try client.read(ServerHello.self)
        try client.writeFrame(Data(#"{"type":"invoke","requestId":"1","op":"messages.observe"}"#.utf8))

        let recording = expectation(description: "recording finished")
        DispatchQueue.global().async {
            for i in 0..<2_000 { self.add(store, "\(i)") }
            recording.fulfill()
        }
        shutdown(client.fd, SHUT_RDWR)
        wait(for: [recording, finished], timeout: 10)
        close(client.fd)
    }

    func testListWithoutLimitUsesDefault() throws {
        let store = MessageStore(maxEntries: 100)
        add(store, "1")
        let (client, finished) = try startSession(store)
        try client.write(ClientHello(protocolVersion: 1, clientName: "t", clientVersion: "t"))
        _ = try client.read(ServerHello.self)
        try client.writeFrame(Data(#"{"type":"invoke","requestId":"1","op":"messages.list"}"#.utf8))
        let reply = try XCTUnwrap(client.readFrame())
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: reply) as? [String: Any])
        XCTAssertNil(object["error"])
        XCTAssertEqual(((object["output"] as? [String: Any])?["messages"] as? [Any])?.count, 1)
        shutdown(client.fd, SHUT_RDWR)
        wait(for: [finished], timeout: 5)
        close(client.fd)
    }

    func testDetailRunsRedactorAfterMasking() throws {
        let store = MessageStore(maxEntries: 100)
        store.add { id, now in
            MessageRecord(
                id: id, requestId: "1", handlerName: "h", direction: .webToApp,
                requestData: #"{"password":"p","email":"a@b.c"}"#, status: .inProgress, requestTimestamp: now
            )
        }
        let seen = Locked<String?>(nil)
        let (client, finished) = try startSession(store) { record in
            record.redacted { message in
                seen.value = message.requestData
                var message = message
                message.requestData = message.requestData?.replacingOccurrences(of: "a@b.c", with: "***")
                return message
            }
        }
        try client.write(ClientHello(protocolVersion: 1, clientName: "t", clientVersion: "t"))
        _ = try client.read(ServerHello.self)
        try client.writeFrame(Data(#"{"type":"invoke","requestId":"1","op":"settings.update","input":{"maskingEnabled":true}}"#.utf8))
        _ = try client.readFrame()
        try client.writeFrame(Data(#"{"type":"invoke","requestId":"2","op":"messages.detail","input":{"id":1}}"#.utf8))
        let reply = try XCTUnwrap(client.readFrame())
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: reply) as? [String: Any])
        let requestData = try XCTUnwrap((object["output"] as? [String: Any])?["requestData"] as? String)
        XCTAssertFalse(try XCTUnwrap(seen.value).contains(#""p""#), "masking runs before the redactor")
        XCTAssertTrue(requestData.contains("***") && !requestData.contains("a@b.c"))
        shutdown(client.fd, SHUT_RDWR)
        wait(for: [finished], timeout: 5)
        close(client.fd)
    }

    func testSearchMatchesRedactedText() throws {
        let store = MessageStore(maxEntries: 100)
        store.add { id, now in
            MessageRecord(
                id: id, requestId: "1", handlerName: "h", direction: .webToApp,
                requestData: "secret-123", status: .inProgress, requestTimestamp: now
            )
        }
        let (client, finished) = try startSession(store) { record in
            record.redacted { message in
                var message = message
                message.requestData = "hidden"
                return message
            }
        }
        try client.write(ClientHello(protocolVersion: 1, clientName: "t", clientVersion: "t"))
        _ = try client.read(ServerHello.self)
        for (requestId, query, expected) in [("1", "secret", 0), ("2", "hidden", 1)] {
            try client.writeFrame(Data(#"{"type":"invoke","requestId":"\#(requestId)","op":"messages.search","input":{"query":"\#(query)"}}"#.utf8))
            let reply = try XCTUnwrap(client.readFrame())
            let object = try XCTUnwrap(JSONSerialization.jsonObject(with: reply) as? [String: Any])
            XCTAssertEqual(((object["output"] as? [String: Any])?["ids"] as? [Any])?.count, expected, query)
        }
        shutdown(client.fd, SHUT_RDWR)
        wait(for: [finished], timeout: 5)
        close(client.fd)
    }

    private func startSession(
        _ store: MessageStore,
        redact: @escaping (MessageRecord) -> MessageRecord = { $0 }
    ) throws -> (FrameChannel, XCTestExpectation) {
        var fds: [Int32] = [0, 0]
        XCTAssertEqual(socketpair(AF_UNIX, SOCK_STREAM, 0, &fds), 0)
        let finished = expectation(description: "session ended")
        let server = fds[0]
        Thread {
            InspectorSession(channel: FrameChannel(fd: server), appInfo: { self.app }, store: store, redact: redact).run()
            close(server)
            finished.fulfill()
        }.start()
        return (FrameChannel(fd: fds[1]), finished)
    }

    private func add(_ store: MessageStore, _ requestId: String) {
        store.add { id, now in
            MessageRecord(id: id, requestId: requestId, handlerName: "h", direction: .webToApp, status: .inProgress, requestTimestamp: now)
        }
    }
}

private final class Locked<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: Value
    init(_ value: Value) { stored = value }
    var value: Value {
        get { lock.withLock { stored } }
        set { lock.withLock { stored = newValue } }
    }
}
