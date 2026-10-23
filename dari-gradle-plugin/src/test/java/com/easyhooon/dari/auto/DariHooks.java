package com.easyhooon.dari.auto;

import android.webkit.WebView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Records hook calls; descriptors must match the real DariHooks in :dari.
public final class DariHooks {
    public static final List<String> calls = new ArrayList<>();

    public static void onMappedCall(String spec, Object receiver, Object[] args) {
        String owner = receiver == null ? "null" : receiver.getClass().getSimpleName();
        calls.add("mapped " + spec + " " + owner + " " + Arrays.toString(args));
    }

    public static void addJavascriptInterface(WebView webView, Object bridge, String name) {
        calls.add("add " + name);
        webView.addJavascriptInterface(bridge, name);
    }

    public static void onVisitedHistory(WebView webView, String url) {
        calls.add("url " + url);
    }
}
