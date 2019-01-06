package com.example.maartenvrijens.myapplication;

import android.renderscript.Allocation;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import org.prebid.mobile.core.AdUnitBidMap;
import org.prebid.mobile.core.LogUtil;
import org.prebid.mobile.core.Prebid;

public class LineItemEventListener extends AdListener {
    public PublisherAdView adView;
    public  LineItemEventListener(PublisherAdView adView){
        this.adView = adView;
    }
    @Override
    public void onAdClosed() {
        super.onAdClosed();
        LogUtil.d("DPF-Banner", "OnAdClosed");
    }

    @Override
    public void onAdFailedToLoad(int i) {
		super.onAdFailedToLoad(i);
        if(i == 3){//3=ERROR_CODE_NO_FILL
            Prebid.adUnitReceivedDefault(this.adView);
        }
        Prebid.markAdUnitLoaded(this.adView);
        LogUtil.d("DPF-Banner", "OnAdFailedToLoad");
    }

    @Override
    public void onAdLeftApplication() {
        super.onAdLeftApplication();
        LogUtil.d("DPF-Banner", "onAdLeftApplication");
    }

    @Override
    public void onAdOpened() {
        super.onAdOpened();
        LogUtil.d("DPF-Banner", "onAdOpened");
    }

    @Override
    public void onAdLoaded() {
        super.onAdLoaded();
        LogUtil.d("DPF-Banner", "onAdLoaded");
        Prebid.markAdUnitLoaded(this.adView);
        Prebid.gatherStats();//tODO find a good location to trigger this
    }


}
