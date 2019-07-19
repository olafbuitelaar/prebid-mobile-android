package org.prebid.mobile;

import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.testutils.BaseSetup;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowWebView;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;


@RunWith(RobolectricTestRunner.class)
@Config(sdk = BaseSetup.testSDK)
public class CacheManagerTest extends BaseSetup {

    @Test
    public void testSaveBidResponse()
    {
        PrebidMobile.setApplicationContext(activity.getApplicationContext());
        PrebidMobile.initCacheManager();

        CacheManager manager = CacheManager.getCacheManager();
        ArrayList<BidResponse> bidResponseList = new ArrayList<>();
        CacheManager.CacheListener mockListener = mock(CacheManager.CacheListener.class);

        // Initialize the cache again, this is done async, and `initCacheManager` doesn't initialize but schedules initialization.
        ShadowLooper cacheLooper = shadowOf(manager.getHandler().getLooper());
        cacheLooper.runToEndOfTasks();
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        // WebView is now available
        ShadowWebView shadowWebView = shadowOf(manager.getWebView());
        assertEquals("https://pubads.g.doubleclick.net", shadowWebView.getLastLoadDataWithBaseURL().baseUrl);
        shadowWebView.getWebViewClient().onPageFinished(manager.getWebView(), "https://pubads.g.doubleclick.net");

        // Now we can save our bid response and test if the cache script is created
        manager.saveBidResponses(bidResponseList, mockListener);
        assertEquals("javascript: \n" +
                "var items = [];\n" +
                "for (var i = 0; i < items.length; i++) {\n" +
                "  var item = items[i];\n" +
                "  localStorage.setItem(item.creative, JSON.stringify(item.bid));\n" +
                "}\n" +
                "Android.cacheSaved();\n", shadowWebView.getLastLoadedUrl());
    }

}
