import SwiftUI
import WebKit

struct BridgeWebView: UIViewRepresentable {
    func makeCoordinator() -> SampleBridge {
        SampleBridge()
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.userContentController.add(context.coordinator, name: SampleBridge.name)
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView
        if let page = Bundle.main.url(forResource: "bridge", withExtension: "html"),
           let html = try? String(contentsOf: page, encoding: .utf8) {
            // An https base URL gives the desktop a readable page URL and origin.
            webView.loadHTMLString(html, baseURL: URL(string: "https://sample.dari.local/bridge"))
        }
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}
}
