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

    private func startSession(_ store: MessageStore) throws -> (FrameChannel, XCTestExpectation) {
        var fds: [Int32] = [0, 0]
        XCTAssertEqual(socketpair(AF_UNIX, SOCK_STREAM, 0, &fds), 0)
        let finished = expectation(description: "session ended")
        let server = fds[0]
        Thread {
            InspectorSession(channel: FrameChannel(fd: server), appInfo: { self.app }, store: store).run()
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
