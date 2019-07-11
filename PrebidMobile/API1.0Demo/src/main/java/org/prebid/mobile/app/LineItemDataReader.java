package org.prebid.mobile.app;

import com.google.android.gms.ads.doubleclick.AppEventListener;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import org.prebid.mobile.LogUtil;
import org.prebid.mobile.PrebidMobile;


public class LineItemDataReader implements AppEventListener {

    public PublisherAdView adView;

    public LineItemDataReader(){

    }

    @Override
    public void onAppEvent(String name, String data) {
        LogUtil.d("received app event " + name +  " " + data);
        PrebidMobile.adUnitReceivedAppEvent(this.adView, name, data);

    }
}
