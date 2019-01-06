package com.example.maartenvrijens.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import org.prebid.mobile.core.AdUnit;
import org.prebid.mobile.core.BannerAdUnit;
import org.prebid.mobile.core.Prebid;
import org.prebid.mobile.core.PrebidException;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements Prebid.OnAttachCompleteListener{
    private PublisherAdView adView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ArrayList<AdUnit> adUnits = new ArrayList<AdUnit>();
        BannerAdUnit adUnit = new BannerAdUnit("Banner1", "test-imp-id");
        adUnits.add(adUnit);
        adUnit.addSize(320,50);
        try{
            Prebid.init(getApplicationContext(), adUnits, "0",Prebid.AdServer.DFP,Prebid.Host.ADSOLUTIONS, "DemoApp", new LineItemDataReader());
        }
        catch (PrebidException e){

        }
        FrameLayout adFrame = (FrameLayout) this.findViewById(R.id.banner);
        adView = new PublisherAdView(this);
        adView.setAdUnitId("/2172982/mobile-sdk");
        adView.setAdSizes(new AdSize(300,250));
        adView.setAdListener(new LineItemEventListener(adView));
        adFrame.addView(adView);
        PublisherAdRequest.Builder builder = new PublisherAdRequest.Builder();
        PublisherAdRequest request = builder.build();
        Prebid.attachBidsWhenReady(request, adView, "Banner1", this,1000, this);

    }

    @Override
    public void onAttachComplete(Object adView,Object adObj) {
        if (adView instanceof PublisherAdView  &&adObj instanceof PublisherAdRequest){
            ((PublisherAdView)adView).loadAd((PublisherAdRequest) adObj);
            Prebid.detachUsedBid(adObj);
        }
    }
}
