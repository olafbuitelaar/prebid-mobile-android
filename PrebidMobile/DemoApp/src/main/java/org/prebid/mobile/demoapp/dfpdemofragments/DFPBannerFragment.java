package org.prebid.mobile.demoapp.dfpdemofragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.doubleclick.AppEventListener;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import org.prebid.mobile.core.LogUtil;
import org.prebid.mobile.core.Prebid;
import org.prebid.mobile.demoapp.Constants;
import org.prebid.mobile.demoapp.LineItemEventListener;
import org.prebid.mobile.demoapp.R;


public class DFPBannerFragment extends Fragment implements Prebid.OnAttachCompleteListener {
    PublisherAdView adView1;
    PublisherAdView adView2;
    private View root;
    private AdListener adListener;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        root = inflater.inflate(R.layout.fragment_banner, null);



        Button btnLoad = (Button) root.findViewById(R.id.loadBanner);
        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadBanner();
            }
        });
        adListener = new AdListener() {
            @Override
            public void onAdClosed() {
                super.onAdClosed();
                LogUtil.d("DPF-Banner", "OnAdClosed");
            }

            @Override
            public void onAdFailedToLoad(int i) {
                super.onAdFailedToLoad(i);
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
            }
        };

        //setupBannerWithoutWait();

        setupBannerWithWait(50000);
        
        return root;
    }

    private void setupBannerWithoutWait() {
        FrameLayout adFrame = (FrameLayout) root.findViewById(R.id.adFrame);
        adFrame.removeAllViews();
        adView1 = new PublisherAdView(getActivity());
        adView1.setAdUnitId(Constants.DFP_BANNER_ADUNIT_ID_320x50);
        adView1.setAdSizes(new AdSize(320, 50));
        adView1.setAdListener(adListener);
        adFrame.addView(adView1);
        //region PriceCheckForDFP API usage
        PublisherAdRequest.Builder builder = new PublisherAdRequest.Builder();
        PublisherAdRequest request = builder.build();
        Prebid.attachBids(request, Constants.BANNER_320x50, this.getActivity());
        //endregion
        adView1.loadAd(request);
    }


    public String getDFPWebViewName() {
        int count = adView2.getChildCount();
        for (int i = 0; i < count; i++) {
            ViewGroup nextChild = (ViewGroup) adView2.getChildAt(i);
            int secondCount = nextChild.getChildCount();
            for (int j = 0; j < secondCount; j++) {
                ViewGroup thirdChild = (ViewGroup) nextChild.getChildAt(j);
                int thirdCount = thirdChild.getChildCount();
                for (int k = 0; k < thirdCount; k++) {
                    System.out.println(thirdChild.getChildAt(k));
                    if (thirdChild.getChildAt(k) instanceof WebView) {
                        return thirdChild.getChildAt(k).getClass().getName();
                    }
                }
            }
        }
        return "undefined";
    }
    
    private void setupBannerWithWait(final int waitTime) {

        FrameLayout adFrame = (FrameLayout) root.findViewById(R.id.adFrame2);
        adFrame.removeAllViews();
        adView2 = new PublisherAdView(getActivity());
        adView2.setAdUnitId(Constants.DFP_BANNER_ADUNIT_ID_300x250);
        adView2.setAdSizes(new AdSize(300, 250));
        //adView2.setAdListener(adListener);
        adView2.setAdListener(new LineItemEventListener(adView2));
        adFrame.addView(adView2);
        //region PriceCheckForDFP API usage
        PublisherAdRequest.Builder builder = new PublisherAdRequest.Builder();


        /*adView2.setAppEventListener(new AppEventListener() {
            @Override
            public void onAppEvent(String name, String data) {

                // The DFP ad that this fragment loads contains JavaScript code that sends App
                // Events to the host application. This AppEventListener receives those events,
                // and sets the background of the fragment to match the data that comes in.
                // The ad will send "red" when it loads, "blue" five seconds later, and "green"
                // if the user taps the ad.

                // This is just a demonstration, of course. Your apps can do much more interesting
                // things with App Events.

                if (name.equals("deliveryData")) {
                    LogUtil.d("DPF-Banner", "onAppEvent" + name+":"+ data);
                }
            }
        });*/


        PublisherAdRequest request = builder.build();

        Prebid.attachBidsWhenReady(request, adView2, Constants.BANNER_300x250, this, waitTime, this.getActivity());
        //endregion

    }

    public void loadBanner() {
        if (adView1 != null) {
            adView1.destroy();
            setupBannerWithoutWait();
        }
        if (adView2 != null) {
            adView2.destroy();
            setupBannerWithWait(50000);
        }
    }

    @Override
    public void onAttachComplete(Object adView, Object adObj) {
        if (adView instanceof PublisherAdView  && adObj instanceof PublisherAdRequest) {
            ((PublisherAdView)adView).loadAd((PublisherAdRequest) adObj);
            Prebid.detachUsedBid(adObj);
        }
    }
}
