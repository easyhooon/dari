package android.webkit;

import java.util.ArrayList;
import java.util.List;

// JVM stand-in for the platform class, so transformed fixtures can run in unit tests.
public class WebView {
    public final List<String> added = new ArrayList<>();

    public final List<String> scripts = new ArrayList<>();

    public void addJavascriptInterface(Object bridge, String name) {
        added.add(name);
    }

    public void evaluateJavascript(String script, ValueCallback<String> callback) {
        scripts.add(script);
    }
}
