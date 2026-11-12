#if os(iOS)
import Dari
import SwiftUI
import UIKit

extension Dari {
    /// Shows the in-app inspector over the frontmost screen, e.g. from your app's debug menu.
    /// Does nothing when it's already showing or there's no window yet.
    @MainActor
    public func presentInspector() {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        guard let window = scene?.windows.first(where: \.isKeyWindow) ?? scene?.windows.first,
              var top = window.rootViewController
        else { return }
        while let presented = top.presentedViewController { top = presented }
        guard !(top is InspectorHostingController) else { return }
        top.present(InspectorHostingController(rootView: DariInspectorView()), animated: true)
    }
}

final class InspectorHostingController: UIHostingController<DariInspectorView> {}
#endif
