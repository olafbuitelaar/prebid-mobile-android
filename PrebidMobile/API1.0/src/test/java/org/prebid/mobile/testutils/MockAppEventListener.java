package org.prebid.mobile.testutils;

import com.google.android.gms.ads.doubleclick.AppEventListener;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import org.prebid.mobile.PrebidMobile;

public class MockAppEventListener implements AppEventListener {
    public PublisherAdView adView;

    @Override
    public void onAppEvent(String name, String data) {
        PrebidMobile.adUnitReceivedAppEvent(this.adView, name, data);
    }
}
