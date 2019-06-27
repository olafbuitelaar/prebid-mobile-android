package org.prebid.mobile;;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;

class CacheManager {

    private WebView dfpWebCache;
    private boolean isWebContextLoaded = false;
    private CacheManagerJavascriptInterface cacheManagerJavascriptInterface;
    private List<CompletionListener> completionListeners = new ArrayList<>();
    private static CacheManager cache;

    private static final int REMOVE_CACHE_INTERVAL = 500;



    private CacheManager(Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        initializeContext(handler, context);
    }

    interface CompletionListener {
        void onCompleted();
    }


    interface CacheListener {
        void cacheSaved();
    }

    public static void init(Context context) {
        if (cache == null) {
            cache = new CacheManager(context);
        }
    }

    public static CacheManager getCacheManager() {
        return cache;
    }

    private void cacheBidResponses(List<BidResponse> bidResponses, CacheListener cacheListener) {
        if (dfpWebCache == null) {
            return;
        }


        List<String> jsObjects = new ArrayList<>();
        for (BidResponse bidResponse : bidResponses) {
            //String escapedBid = StringUtils.escapeEcmaScript(bidResponse.toString());
            String cacheId = bidResponse.getCreative();
            String bidStr = bidResponse.toString();
            String jsObject = String.format("{creative: '%s', bid: %s}", cacheId, bidStr);
            jsObjects.add(jsObject);
        }

        String arrayString = String.format("var items = [%s];", TextUtils.join(",", jsObjects));
        String jsScript = "" +
                "\n" +
                arrayString +
                "\n" +
                "for (var i = 0; i < items.length; i++) {\n" +
                "  var item = items[i];\n" +
                "  localStorage.setItem(item.creative, JSON.stringify(item.bid));\n" + //Changed to JSON.stringify, was escapeEcmaScript
                "}" +
                "\n" +
                "Android.cacheSaved();" +
                "\n" +
                "";

        cacheManagerJavascriptInterface.setListener(cacheListener);
        dfpWebCache.loadUrl("javascript: " + jsScript + ";");
    }


    public void saveBidResponses(final List<BidResponse> bidResponses, final CacheListener cacheListener) {
        if (isWebContextLoaded) {
            cacheBidResponses(bidResponses, cacheListener);
        } else {
            completionListeners.add(new CompletionListener() {
                @Override
                public void onCompleted() {
                    cacheBidResponses(bidResponses, cacheListener);
                }
            });
        }
    }


    private void removeCache(long now, final Handler handler) {


        String removeWebCache = "var currentTime = " + String.valueOf(now) + ";" +
                "\nvar toBeDeleted = [];\n" +
                "\n" +
                "for(i = 0; i< localStorage.length; i ++) {\n" +
                "\tif (localStorage.key(i).startsWith('Prebid_')) {\n" +
                "\t\tcreatedTime = localStorage.key(i).split('_')[2];\n" +
                "\t\tif (( currentTime - createdTime) > 270000){\n" +
                "\t\t\ttoBeDeleted.push(localStorage.key(i));\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}\n" +
                "\n" +
                "for ( i = 0; i< toBeDeleted.length; i ++) {\n" +
                "\tlocalStorage.removeItem(toBeDeleted[i]);\n" +
                "}";
        if (dfpWebCache != null) {
            dfpWebCache.loadUrl("javascript: "+removeWebCache);
        }


        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                removeCache(System.currentTimeMillis(), handler);
            }
        }, CacheManager.REMOVE_CACHE_INTERVAL);
    }

    private void initializeContext(final Handler handler, final Context context) {

        handler.post(new Runnable() {
            @Override
            public void run() {


                dfpWebCache = new WebView(context);

                cacheManagerJavascriptInterface = new CacheManagerJavascriptInterface(context, dfpWebCache);
                dfpWebCache.addJavascriptInterface(cacheManagerJavascriptInterface, "Android");

                WebSettings webSettings = dfpWebCache.getSettings();
                if (webSettings != null) {
                    webSettings.setDomStorageEnabled(true);
                    webSettings.setJavaScriptEnabled(true);
                }

                dfpWebCache.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {

                        isWebContextLoaded = true;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                removeCache(System.currentTimeMillis(), handler);
                            }
                        });

                        for (CompletionListener completionListener : completionListeners) {
                            completionListener.onCompleted();
                        }

                        completionListeners = new ArrayList<>();


                    }
                });
                dfpWebCache.loadDataWithBaseURL("https://pubads.g.doubleclick.net", "<html></html>", "text/html", null, null);

            }
        });

    }

}