// swift-tools-version:5.9
import CompilerPluginSupport
import PackageDescription

let package = Package(
    name: "Dari",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "Dari", targets: ["Dari"]),
        .library(name: "DariMacros", targets: ["DariMacros"]),
        // In-app inspector screens; apps that only use the desktop app don't link it.
        .library(name: "DariUI", targets: ["DariUI"]),
    ],
    dependencies: [
        // Only DariMacros builds it, on the host; apps that use just Dari never compile it.
        .package(url: "https://github.com/swiftlang/swift-syntax", "600.0.0"..<"700.0.0"),
    ],
    targets: [
        .target(name: "Dari"),
        .macro(
            name: "DariMacrosPlugin",
            dependencies: [
                .product(name: "SwiftSyntaxMacros", package: "swift-syntax"),
                .product(name: "SwiftCompilerPlugin", package: "swift-syntax"),
            ]
        ),
        .target(name: "DariMacros", dependencies: ["Dari", "DariMacrosPlugin"]),
        .target(name: "DariUI", dependencies: ["Dari"]),
        // Serves a few canned messages on the Mac's loopback, the same path a simulator app uses.
        .executableTarget(name: "DariLoopbackDemo", dependencies: ["Dari"]),
        .testTarget(name: "DariTests", dependencies: ["Dari", "DariMacros"], resources: [.copy("Fixtures")]),
        .testTarget(name: "DariUITests", dependencies: ["Dari", "DariUI"]),
        .testTarget(
            name: "DariMacrosTests",
            dependencies: [
                "DariMacrosPlugin",
                .product(name: "SwiftSyntaxMacrosTestSupport", package: "swift-syntax"),
            ]
        ),
    ]
)
