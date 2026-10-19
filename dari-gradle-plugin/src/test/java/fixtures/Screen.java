package fixtures;

import android.webkit.WebView;

public class Screen {
    public void setup(WebView webView, Object bridge) {
        webView.addJavascriptInterface(bridge, "Android");
    }

    public void setupSubclass(MyWebView webView) {
        webView.addJavascriptInterface(this, "Sub");
    }
}
