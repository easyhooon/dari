import DariMacros
import SwiftUI

@main
struct DariSampleApp: App {
    init() {
        // Debug and Staging only (Staging sets the DARI condition): while Dari runs, anything on the
        // device's loopback can read bridge messages. Release never calls start(), so Dari records nothing.
        #if DEBUG || DARI
        do {
            // SampleBridge records itself with @DariBridge. `-DariAutoCapture YES` hooks WebKit instead.
            let autoCapture = UserDefaults.standard.bool(forKey: "DariAutoCapture") ? SampleCapture.autoCapture : nil
            try Dari.shared.start(DariConfiguration(autoCapture: autoCapture))
            Dari.shared.setEnvironment(["sample": "ios"])
        } catch {
            print("Dari could not start: \(error)")
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            BridgeWebView()
        }
    }
}
