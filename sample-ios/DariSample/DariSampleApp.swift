import Dari
import SwiftUI

@main
struct DariSampleApp: App {
    init() {
        // Debug and Staging only (Staging sets the DARI condition): while Dari runs, anything on the
        // device's loopback can read bridge messages. Release never calls start(), so the linker strips Dari.
        #if DEBUG || DARI
        do {
            try Dari.shared.start()
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
