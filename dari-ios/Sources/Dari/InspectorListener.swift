import Foundation

/// Accepts desktop connections on 127.0.0.1. A simulator shares the Mac's loopback; a device's loopback is
/// reached only through usbmuxd, so binding to loopback keeps the socket off the network in both cases.
final class InspectorListener {
    enum Failure: Error, CustomStringConvertible {
        case noFreePort(ClosedRange<UInt16>)
        case posix(String)

        var description: String {
            switch self {
            case let .noFreePort(ports): "No free port in \(ports.lowerBound)...\(ports.upperBound)"
            case let .posix(reason): reason
            }
        }
    }

    private let ports: ClosedRange<UInt16>
    private let onClient: (FrameChannel) -> Void
    private let lock = NSLock()
    private var clients: Set<Int32> = []
    private var running = false
    // A restart must not revive the accept loop of the previous start.
    private var generation = 0

    private(set) var port: UInt16 = 0

    init(ports: ClosedRange<UInt16>, onClient: @escaping (FrameChannel) -> Void) {
        self.ports = ports
        self.onClient = onClient
    }

    var connectedClients: Int { lock.withLock { clients.count } }

    /// Binds the first free port in the span. Several simulators share one loopback, so each app may land on a later port.
    func start() throws {
        try lock.withLock {
            guard !running else { return }
            for candidate in ports {
                if let fd = try bind(candidate) {
                    port = candidate
                    running = true
                    generation += 1
                    let current = generation
                    // Strong capture: the loop must run to close [fd] even if the listener is released right away.
                    let thread = Thread { self.acceptLoop(fd, generation: current) }
                    thread.name = "dari-inspector-accept"
                    thread.start()
                    return
                }
            }
            throw Failure.noFreePort(ports)
        }
    }

    func stop() {
        lock.withLock {
            guard running else { return }
            running = false
            // The accept loop polls, so it notices `running` and closes the listening socket itself.
            clients.forEach { shutdown($0, SHUT_RDWR) }
        }
    }

    private func bind(_ port: UInt16) throws -> Int32? {
        let fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
        guard fd >= 0 else { throw Failure.posix(String(cString: strerror(errno))) }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_port = port.bigEndian
        address.sin_addr.s_addr = INADDR_LOOPBACK.bigEndian
        // Lets a relaunched app reclaim its port from TIME_WAIT; a port with a live listener still fails with EADDRINUSE.
        var on: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &on, socklen_t(MemoryLayout<Int32>.size))
        let bound = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        guard bound == 0, listen(fd, 4) == 0 else {
            close(fd)
            return nil
        }
        return fd
    }

    private func acceptLoop(_ fd: Int32, generation current: Int) {
        defer { close(fd) }
        var poller = pollfd(fd: fd, events: Int16(POLLIN), revents: 0)
        while lock.withLock({ running && generation == current }) {
            guard poll(&poller, 1, Self.pollMillis) > 0 else { continue }
            let client = accept(fd, nil, nil)
            guard client >= 0 else { continue }
            let registered = lock.withLock { running && generation == current ? clients.insert(client).inserted : false }
            guard registered else {
                close(client)
                continue
            }
            let thread = Thread { self.serve(client) }
            thread.name = "dari-inspector-session"
            thread.start()
        }
    }

    private func serve(_ fd: Int32) {
        onClient(FrameChannel(fd: fd))
        lock.withLock { _ = clients.remove(fd) }
        close(fd)
    }

    private static let pollMillis: Int32 = 250
}
