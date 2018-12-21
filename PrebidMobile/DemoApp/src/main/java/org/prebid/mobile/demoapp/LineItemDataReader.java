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
        if (name.equals("deliveryData")) {
            LogUtil.d("DPF-Banner", "onAppEvent" + name+":"+ data);
            String[] serveData = data.split("|");
            AdUnitBidMap bidmap = Prebid.getAdunitMapByAdView(this.adView);
            bidmap.data.lineItemId = serveData[0];
            bidmap.data.creativeId = serveData[1];
            //Prebid.gatherStats();
        }else if(name.equals("wonHB")){
            //TODO: match cache id on available bids, to determine the exact winner
            AdUnitBidMap bidmap = Prebid.getAdunitMapByAdView(this.adView);
            //bidmap.isWinner = true;

            Prebid.markWinner(bidmap.adUnitCode, data);
        }
    }
}
