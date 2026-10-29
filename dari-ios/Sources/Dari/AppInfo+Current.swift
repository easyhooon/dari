import Foundation

extension AppInfo {
    static func current(_ environment: [String: String]) -> AppInfo {
        let bundle = Bundle.main
        let identifier = bundle.bundleIdentifier ?? ProcessInfo.processInfo.processName
        let name = bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
            ?? bundle.object(forInfoDictionaryKey: "CFBundleName") as? String
            ?? ProcessInfo.processInfo.processName
        let version = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let os = ProcessInfo.processInfo.operatingSystemVersion
        return AppInfo(
            applicationId: identifier,
            appName: name,
            appVersion: version,
            dariVersion: Dari.version,
            deviceModel: deviceModel(),
            sdkInt: 0,
            environment: environment.isEmpty ? nil : environment,
            platform: platform,
            osVersion: "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)"
        )
    }

    private static var platform: String {
        #if os(iOS)
        "ios"
        #else
        "macos"
        #endif
    }

    /// Hardware identifier such as `iPhone17,1`; simulators report the model they simulate.
    private static func deviceModel() -> String {
        if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return "\(simulated) (Simulator)"
        }
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
    }
}
