import DariMacros
import DariUI
#if canImport(DariKmpSample)
import DariKmpSample
#endif
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
            try Dari.shared.start(DariConfiguration(persistsMessages: true, autoCapture: autoCapture))
            Dari.shared.setEnvironment(["sample": "ios"])
            // Extra masking for the desktop app, after the built-in one when that is on.
            Dari.shared.setPayloadRedactor { message in
                var message = message
                message.requestData = message.requestData?.replacingOccurrences(
                    of: #""email":"[^"]*""#, with: #""email":"***""#, options: .regularExpression
                )
                return message
            }
        } catch {
            print("Dari could not start: \(error)")
        }
        #if canImport(DariKmpSample)
        // dari-kmp PoC: Kotlin commonMain code records through the Swift SDK. See sample-kmp.
        SharedBridge().simulateTraffic(pageUrl: "https://example.com/kmp?token=1")
        #endif
        #endif
    }

    var body: some Scene {
        WindowGroup {
            BridgeWebView()
                #if DEBUG || DARI
                // Stands in for an app's debug menu: opens the in-app inspector.
                .overlay(alignment: .bottomTrailing) {
                    Button {
                        Dari.shared.presentInspector()
                    } label: {
                        Label("Inspector", systemImage: "list.bullet.rectangle")
                    }
                    .buttonStyle(.borderedProminent)
                    .padding()
                }
                #endif
        }
    }
}
