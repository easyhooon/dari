// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Dari",
    platforms: [.iOS(.v15), .macOS(.v13)],
    products: [
        .library(name: "Dari", targets: ["Dari"]),
    ],
    targets: [
        .target(name: "Dari"),
        // Serves a few canned messages on the Mac's loopback, the same path a simulator app uses.
        .executableTarget(name: "DariLoopbackDemo", dependencies: ["Dari"]),
        .testTarget(name: "DariTests", dependencies: ["Dari"], resources: [.copy("Fixtures")]),
    ]
)
