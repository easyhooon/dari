// swift-tools-version:5.9
// Lets apps add the iOS SDK from this repository's URL. Development (tests, demo) lives in dari-ios/Package.swift.
import CompilerPluginSupport
import PackageDescription

let package = Package(
    name: "Dari",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "Dari", targets: ["Dari"]),
        .library(name: "DariMacros", targets: ["DariMacros"]),
        .library(name: "DariUI", targets: ["DariUI"]),
    ],
    dependencies: [
        // Only DariMacros builds it, on the host; apps that use just Dari never compile it.
        .package(url: "https://github.com/swiftlang/swift-syntax", "600.0.0"..<"700.0.0"),
    ],
    targets: [
        .target(name: "Dari", path: "dari-ios/Sources/Dari"),
        .macro(
            name: "DariMacrosPlugin",
            dependencies: [
                .product(name: "SwiftSyntaxMacros", package: "swift-syntax"),
                .product(name: "SwiftCompilerPlugin", package: "swift-syntax"),
            ],
            path: "dari-ios/Sources/DariMacrosPlugin"
        ),
        .target(name: "DariMacros", dependencies: ["Dari", "DariMacrosPlugin"], path: "dari-ios/Sources/DariMacros"),
        .target(name: "DariUI", dependencies: ["Dari"], path: "dari-ios/Sources/DariUI"),
    ]
)
