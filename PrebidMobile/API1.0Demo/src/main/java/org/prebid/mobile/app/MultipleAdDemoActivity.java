/*
 *    Copyright 2018-2019 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import org.prebid.mobile.AdUnit;
import org.prebid.mobile.BannerAdUnit;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.OnCompleteListener;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.ResultCode;


public class MultipleAdDemoActivity extends AppCompatActivity {

    private PublisherAdView dfpAdViewOne;
    private PublisherAdView dfpAdViewTwo;

    private AdUnit adUnitOne;
    private AdUnit adUnitTwo;
    private Button refreshButton;
    private Button gatherStatsButton;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        refreshButton = findViewById(R.id.refresh_button);
        gatherStatsButton = findViewById(R.id.gather_stats_button);




        LinearLayout adFrame = findViewById(R.id.adFrame);
        adFrame.removeAllViews();

        adUnitOne = new BannerAdUnit("banner1","test-imp-id", 320, 50);
        adUnitTwo = new BannerAdUnit("banner2","test-imp-id", 320, 50);



        dfpAdViewOne = createDFPView();
        dfpAdViewTwo = createDFPView();

        adFrame.addView(dfpAdViewOne);
        adFrame.addView(dfpAdViewTwo);

        PrebidMobile.setAppPage("multipleAdDemoActivity");
        PrebidMobile.setAppListener(new LineItemDataReader());



        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadAdView(dfpAdViewOne, adUnitOne);
                loadAdView(dfpAdViewTwo, adUnitTwo);
            }
        });

        gatherStatsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PrebidMobile.gatherStats();
            }
        });


        loadAdView(dfpAdViewOne, adUnitOne);
        loadAdView(dfpAdViewTwo, adUnitTwo);
    }




    PublisherAdView createDFPView() {
        final PublisherAdView dfpAdView = new PublisherAdView(this);
        dfpAdView.setAdUnitId("/2172982/mobile-sdk");
        dfpAdView.setAdSizes(new AdSize(300, 250));
        dfpAdView.setAdListener(new AdListener(){

            @Override
            public void onAdClosed() {
                super.onAdClosed();
            }

            @Override
            public void onAdFailedToLoad(int i) {
                LogUtil.d("DFP-Banner", "onAdFailedToLoad");
                if(i == 3){
                    PrebidMobile.adUnitReceivedDefault(dfpAdView);
                }
                PrebidMobile.markAdUnitLoaded(dfpAdView);
                super.onAdFailedToLoad(i);
            }

            @Override
            public void onAdLoaded() {
                LogUtil.d("DFP-Banner", "onAdLoaded");
                PrebidMobile.markAdUnitLoaded(dfpAdView);
                super.onAdLoaded();
            }


        });
        return dfpAdView;
    }


    void loadAdView(PublisherAdView dfpAdView, AdUnit adUnit) {
        PublisherAdRequest.Builder builder = new PublisherAdRequest.Builder();
        PublisherAdRequest request = builder.build();

        adUnit.fetchDemand(request, dfpAdView, new OnCompleteListener() {
            @Override
            public void onComplete(ResultCode resultCode, Object adObject, Object adView) {
                PublisherAdView publisherAdView = (PublisherAdView) adView;
                PublisherAdRequest publisherAdRequest = (PublisherAdRequest) adObject;
                publisherAdView.loadAd(publisherAdRequest);
            }
        });


    }



}
