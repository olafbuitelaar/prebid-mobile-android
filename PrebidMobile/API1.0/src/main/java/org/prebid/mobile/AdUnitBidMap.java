package org.prebid.mobile;

public class AdUnitBidMap
{
    public Object adView;
    public String adUnitCode;
    public boolean isWinner = false;
    public boolean isDefault = false;
    public boolean isServerUpdated = false;
    public AdUnitBidData data;

    public AdUnitBidMap(Object adView, String adUnitCode){
        this.adView = adView;
        this.adUnitCode = adUnitCode;
        this.data = new AdUnitBidData();
    }
}
