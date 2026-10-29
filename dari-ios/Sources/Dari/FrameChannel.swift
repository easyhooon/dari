import Foundation

/** Length-prefixed framing over a connected socket: 4-byte big-endian length, then UTF-8 JSON. */
final class FrameChannel {
    enum Failure: Error {
        case invalidLength(Int)
        case closed
        case posix(String)
    }

    let fd: Int32
    private let writeLock = NSLock()

    init(fd: Int32) {
        self.fd = fd
        var on: Int32 = 1
        // A desktop that disconnects mid-write must end the session, not the app.
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &on, socklen_t(MemoryLayout<Int32>.size))
    }

    /** Returns nil on a clean end of stream before a new frame starts. */
    func readFrame() throws -> Data? {
        guard let header = try readExactly(4, allowCleanEOF: true) else { return nil }
        let length = header.reduce(0) { ($0 << 8) | Int($1) }
        guard length >= 0, length <= InspectorProtocol.maxFrameBytes else { throw Failure.invalidLength(length) }
        return try readExactly(length, allowCleanEOF: false) ?? Data()
    }

    func read<T: Decodable>(_ type: T.Type) throws -> T? {
        try readFrame().map { try JSONDecoder().decode(T.self, from: $0) }
    }

    func writeFrame(_ payload: Data) throws {
        guard payload.count <= InspectorProtocol.maxFrameBytes else { throw Failure.invalidLength(payload.count) }
        let length = UInt32(payload.count).bigEndian
        var frame = withUnsafeBytes(of: length) { Data($0) }
        frame.append(payload)
        writeLock.lock()
        defer { writeLock.unlock() }
        try writeAll(frame)
    }

    func write<T: Encodable>(_ value: T) throws {
        try writeFrame(JSONEncoder().encode(value))
    }

    private func readExactly(_ count: Int, allowCleanEOF: Bool) throws -> Data? {
        var buffer = [UInt8](repeating: 0, count: count)
        var offset = 0
        while offset < count {
            let n = buffer.withUnsafeMutableBytes { Darwin.read(fd, $0.baseAddress! + offset, count - offset) }
            if n > 0 {
                offset += n
            } else if n == 0 {
                if offset == 0 && allowCleanEOF { return nil }
                throw Failure.closed
            } else if errno != EINTR {
                throw Failure.posix(String(cString: strerror(errno)))
            }
        }
        return Data(buffer)
    }

    private func writeAll(_ data: Data) throws {
        try data.withUnsafeBytes { raw in
            var offset = 0
            while offset < raw.count {
                let n = Darwin.write(fd, raw.baseAddress! + offset, raw.count - offset)
                if n > 0 {
                    offset += n
                } else if n < 0 && errno == EINTR {
                    continue
                } else {
                    throw Failure.posix(String(cString: strerror(errno)))
                }
            }
        }
    }
}
