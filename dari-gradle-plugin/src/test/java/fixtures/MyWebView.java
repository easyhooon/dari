package fixtures;

import android.webkit.WebView;

public class MyWebView extends WebView {
    public void addSelf() {
        super.addJavascriptInterface(this, "Self");
    }

    public void runSelf() {
        super.evaluateJavascript("self()", null);
    }
}
