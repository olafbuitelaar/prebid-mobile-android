package org.prebid.mobile.demoapp;

import com.google.android.gms.ads.doubleclick.AppEventListener;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import org.prebid.mobile.core.AdUnitBidMap;
import org.prebid.mobile.core.LogUtil;
import org.prebid.mobile.core.Prebid;

public class LineItemDataReader implements AppEventListener {

    public PublisherAdView adView;

    public LineItemDataReader(){

    }

    @Override
    public void onAppEvent(String name, String data) {
        Prebid.adUnitReceivedAppEvent(this.adView, name, data);

    }
}
