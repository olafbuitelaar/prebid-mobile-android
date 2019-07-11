package org.prebid.mobile;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public class CacheManagerJavascriptInterface {
    Context context;
    WebView webView;
    CacheManager.CacheListener cacheListener;

    CacheManagerJavascriptInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
    }

    public void setListener(CacheManager.CacheListener cacheListener) {
        this.cacheListener = cacheListener;
    }

    @JavascriptInterface
    public void cacheSaved() {
        cacheListener.cacheSaved();
    }
}
