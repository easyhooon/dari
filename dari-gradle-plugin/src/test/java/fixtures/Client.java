package fixtures;

import android.webkit.WebView;
import android.webkit.WebViewClient;

public class Client extends WebViewClient {
    public String seen;

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        seen = url;
    }
}
