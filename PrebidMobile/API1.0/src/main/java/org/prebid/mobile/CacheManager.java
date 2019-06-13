package org.prebid.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheManager {
    // This is the class that manages three cache instances, one for DFP, one for MoPub and one for SDK rendered ad
    private WebView dfpWebCache;
    private CacheManagerJavascriptInterface cacheManagerJavascriptInterface;
    private HashMap<String, String> sdkCache;
    private List<BidResponse> pendingWebCache = new ArrayList<>();
    private static CacheManager cache;
    private static boolean webViewLoaded = false;



    private CacheManager(Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        setupWebCache(context, handler);
        setupSDKCache();
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

    private static void setupBidCleanUpRunnable(final Handler handler) {
        handler.post(new BidCleanUpRunnable(getCacheManager(), handler));
    }
    private static void setupBidCleanUpRunnable() {
        Handler handler = new Handler(Looper.getMainLooper());
        setupBidCleanUpRunnable(handler);
    }

    private void removeCache(long now) {
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
            LogUtil.d("cleanling cache id's");
            //dfpWebCache.loadDataWithBaseURL("https://pubads.g.doubleclick.net", removeWebCache, "text/html", null, null);
            dfpWebCache.loadUrl("javascript: "+removeWebCache);
        }
        if (sdkCache != null) {
            ArrayList<String> toBeDeleted = new ArrayList<String>();
            for (String key : sdkCache.keySet()) {
                long createdTime = Long.valueOf(key.split("_")[2]);
                if ((now - createdTime) > 270000) {
                    toBeDeleted.add(key);
                }
            }
            for (String key : toBeDeleted) {
                sdkCache.remove(key);
            }
        }
    }



    public void saveBidResponses(List<BidResponse> bidResponses, CacheListener cacheListener) {



        List<BidResponse> sdkDemand = new ArrayList<>();
        List<BidResponse> webDemand = new ArrayList<>();

        for (BidResponse bidResponse : bidResponses) {
            String format = bidResponse.getFormat();
            if (format.equals("html")) {
                webDemand.add(bidResponse);
            } else if (format.equals("demand_sdk")) {
                sdkDemand.add(bidResponse);
            }
        }

        for (BidResponse sdkBid : sdkDemand) {
            saveCacheForSDK(sdkBid.getCreative(), sdkBid.toString());
        }


        if (webDemand.size() > 0) {
            saveCacheForWeb(webDemand, cacheListener);
        } else {
            cacheListener.cacheSaved();
        }


    }


    private void setupWebCache(final Context context, final Handler handler) {
        handler.postAtFrontOfQueue(new WebCacheInitializer(this, context));
    }

    private void saveCacheForWeb(final List<BidResponse> bidResponses, final CacheListener cacheListener) {
        if(!webViewLoaded) {
            final CacheManager that=this;
            pendingWebCache.addAll(bidResponses);
            dfpWebCache.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    webViewLoaded = true;
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postAtFrontOfQueue(new SaveCacheForWebRunnable(that, pendingWebCache, cacheListener));
                }
            });
        }else {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postAtFrontOfQueue(new SaveCacheForWebRunnable(this, bidResponses, cacheListener));
        }
    }

    private void setupSDKCache() {
        if (sdkCache == null) {
            sdkCache = new HashMap<String, String>();
        }
    }

    private void saveCacheForSDK(String cacheId, String bid) {
        if (sdkCache != null) {
            sdkCache.put(cacheId, bid);
        }
    }

    private String getCacheForSDK(String cacheId) {
        if (sdkCache != null) {
            return sdkCache.remove(cacheId);
        }
        return null;
    }

    private static class BidCleanUpRunnable implements Runnable {
        private final WeakReference<CacheManager> cacheManagerWeakRef;
        private final Handler handler;

        BidCleanUpRunnable(final CacheManager cacheManager, final Handler handler) {
            cacheManagerWeakRef = new WeakReference<>(cacheManager);
            this.handler = handler;
        }

        @Override
        public void run() {
            CacheManager cacheManager = cacheManagerWeakRef.get();
            if (cacheManager == null) {
                return;
            }
            cacheManager.removeCache(System.currentTimeMillis());
            handler.postDelayed(this, 270000);
        }
    }



    private static class WebCacheInitializer implements Runnable {
        private final WeakReference<CacheManager> cacheManagerWeakRef;
        private final WeakReference<Context> contextWeakRef;

        WebCacheInitializer(final CacheManager cacheManager,final  Context context) {
            cacheManagerWeakRef = new WeakReference<>(cacheManager);
            contextWeakRef = new WeakReference<>(context);
        }

        /**/


        @Override
        public void run() {
            final CacheManager cacheManager = cacheManagerWeakRef.get();
            if (cacheManager == null) {
                return;
            }

            Context context = contextWeakRef.get();
            if (context == null) {
                return;
            }

            try {
                cacheManager.dfpWebCache = new WebView(context);
                cacheManager.cacheManagerJavascriptInterface = new CacheManagerJavascriptInterface(context, cacheManager.dfpWebCache);
                cacheManager.dfpWebCache.addJavascriptInterface(cacheManager.cacheManagerJavascriptInterface, "Android");
                WebSettings webSettings = cacheManager.dfpWebCache.getSettings();
                if (webSettings != null) {
                    webSettings.setDomStorageEnabled(true);
                    webSettings.setJavaScriptEnabled(true);
                }

                cacheManager.dfpWebCache.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        cacheManager.webViewLoaded = true;
                    }
                });
                cacheManager.dfpWebCache.loadDataWithBaseURL("https://pubads.g.doubleclick.net", "<html></html>", "text/html", null, null);

                //cacheManager.dfpWebCache.addJavascriptInterface(new JavaScriptInterface(), "confirmer");
                CacheManager.setupBidCleanUpRunnable();
            } catch (Throwable t) {
                // possible AndroidRuntime thrown at android.webkit.WebViewFactory.getFactoryClass
                // stemming from WebView's constructor, manifests itself in Android 5.0 and 5.1.
            }
        }
    }



    private static class SaveCacheForWebRunnable implements Runnable {
        private final WeakReference<CacheManager> cacheManagerWeakRef;
        private final List<BidResponse> bidResponses;
        private final CacheListener cacheListener;

        SaveCacheForWebRunnable(CacheManager cacheManager, final List<BidResponse> bidResponses, final CacheListener cacheListener) {
            cacheManagerWeakRef = new WeakReference<>(cacheManager);
            this.bidResponses = bidResponses;
            this.cacheListener = cacheListener;
        }




        @Override
        public void run() {
            CacheManager cacheManager = cacheManagerWeakRef.get();
            if (cacheManager == null) {
                return;
            }
            if (cacheManager.dfpWebCache == null) {
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

            cacheManager.cacheManagerJavascriptInterface.setListener(cacheListener);
            cacheManager.dfpWebCache.loadUrl("javascript: " + jsScript + ";");


        }
    }
}
