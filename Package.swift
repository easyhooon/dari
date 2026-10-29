// swift-tools-version:5.9
// Lets apps add the iOS SDK from this repository's URL. Development (tests, demo) lives in dari-ios/Package.swift.
import PackageDescription

let package = Package(
    name: "Dari",
    platforms: [.iOS(.v15), .macOS(.v13)],
    products: [
        .library(name: "Dari", targets: ["Dari"]),
    ],
    targets: [
        .target(name: "Dari", path: "dari-ios/Sources/Dari"),
    ]
)
