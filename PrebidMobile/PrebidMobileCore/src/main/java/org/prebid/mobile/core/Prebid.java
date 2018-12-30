/*
 *    Copyright 2016 Prebid.org, Inc.
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

package org.prebid.mobile.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prebid class is the Entry point for Apps in the Prebid Module.
 */
public class Prebid {
    private static Context context;
    private static String PREBID_SERVER = "org.prebid.mobile.prebidserver.PrebidServerAdapter";

    private static String MOPUB_ADVIEW_CLASS = "com.mopub.mobileads.MoPubView";
    private static String MOPUB_INTERSTITIAL_CLASS = "com.mopub.mobileads.MoPubInterstitial";
    private static String DFP_ADREQUEST_CLASS = "com.google.android.gms.ads.doubleclick.PublisherAdRequest";

    private static boolean secureConnection = true; //by default, always use secured connection
    private static String accountId;
    private static final int kMoPubQueryStringLimit = 4000;
    private static boolean useLocalCache = true;
    private static Host host = Host.APPNEXUS;
    private static AdServer adServer = AdServer.UNKNOWN;
    private static Map<String, List<Object>> bidAdunitMap = new HashMap<String, List<Object>>();
    private static Object appListener;
    private static String appName;
    private static String appPage;

    public static void markWinner(String adUnitCode, String creativeId) {
        ArrayList<BidResponse> bids = BidManager.getBidsForAdUnit(adUnitCode);
        for (BidResponse bid : bids) {
            String crt = bid.getCreative();
            if(bid.getCreative().equals(creativeId)){
                bid.setWinner();
            }
        }
    }

    public enum AdServer {
        DFP,
        MOPUB,
        UNKNOWN
    }

    public enum Host {
        APPNEXUS,
        RUBICON,
        ADSOLUTIONS,
        ADSOLUTIONS_DEV
    }

    //region Public APIs

    /**
     * Listener Interface to be used with attachTopBidWhenReady for Banner.
     */
    public interface OnAttachCompleteListener {

        /**
         * Called whenever the bid has been attached to the Banner view, or when the timeout has occurred. Which ever is the earliest.
         */
        void onAttachComplete(Object adObj);
    }

    public static AdServer getAdServer() {
        return adServer;
    }


    public static Host getHost() {
        return host;
    }

    public static boolean useLocalCache() {
        return useLocalCache;
    }

    /**
     * This method is used to:
     * - Validate inputs of ad units
     * - Validate the setup of the demand adapter
     * - Start the bid manager
     *
     * @param context   Application context
     * @param adUnits   List of Ad Slot Configurations to register
     * @param accountId Prebid Server account
     * @param adServer  Primary AdServer you're using for your app
     * @param host      Host you're using for your app
     * @param appName   The name of the app
     * @param appListener   the adView applistener from the app
     * @throws PrebidException
     */
    public static void init(Context context, ArrayList<AdUnit> adUnits, String accountId, AdServer adServer, Host host, String appName, Object appListener) throws PrebidException {
        LogUtil.i("Initializing with a list of AdUnits");
        // validate context
        if (context == null) {
            throw new PrebidException(PrebidException.PrebidError.NULL_CONTEXT);
        }

        // validate account id
        if (TextUtils.isEmpty(accountId)) {
            throw new PrebidException(PrebidException.PrebidError.INVALID_ACCOUNT_ID);
        }

        Prebid.context = context;

        Prebid.appListener = appListener;
        Prebid.accountId = accountId;
        Prebid.adServer = adServer;
        Prebid.appName = appName;
        if (AdServer.MOPUB.equals(Prebid.adServer)) {
            Prebid.useLocalCache = false;
        }
        if (host == null)
            throw new PrebidException(PrebidException.PrebidError.NULL_HOST);
        Prebid.host = host;
        // validate ad units and register them
        if (adUnits == null || adUnits.isEmpty()) {
            throw new PrebidException(PrebidException.PrebidError.EMPTY_ADUNITS);
        }
        for (AdUnit adUnit : adUnits) {
            if (adUnit.getAdType().equals(AdType.BANNER) && adUnit.getSizes().isEmpty()) {
                LogUtil.e("Sizes are not added to BannerAdUnit with code: " + adUnit.getCode());
                throw new PrebidException(PrebidException.PrebidError.BANNER_AD_UNIT_NO_SIZE);
            }
            if (adUnit.getAdType().equals(AdType.INTERSTITIAL)) {
                ((InterstitialAdUnit) adUnit).setInterstitialSizes(context);
            }
            BidManager.registerAdUnit(adUnit);
        }
        // set up demand adapter

        try {
            Class<?> adapterClass = Class.forName(PREBID_SERVER);
            DemandAdapter adapter = (DemandAdapter) adapterClass.newInstance();
            if (adapter != null) {
                BidManager.adapter = adapter;
            } else {
                throw new PrebidException(PrebidException.PrebidError.UNABLE_TO_INITIALIZE_DEMAND_SOURCE);
            }
        } catch (Exception e) {
            throw new PrebidException(PrebidException.PrebidError.UNABLE_TO_INITIALIZE_DEMAND_SOURCE);
        }
        // set up bid manager
        //BidManager.setBidsExpirationRunnable(context); //FOR NOW DON'T AUTO EXPIRE BIDS //TODO: improve!
        // set up cache manager
        CacheManager.init(context);
        // start ad requests
        BidManager.requestBidsForAdUnits(context, adUnits);
    }
    public static void setAppPage(String appPage){
        Prebid.appPage = appPage;
    }

    public static void attachBids(Object adObj, String adUnitCode, Context context) {
        if (adObj == null) {
            //LogUtil.e(TAG, "Request is null, unable to set keywords");
        } else {
            detachUsedBid(adObj);
            markBidsSend(adUnitCode);

            if (adObj.getClass() == getClassFromString(MOPUB_ADVIEW_CLASS)
                    || adObj.getClass() == getClassFromString(MOPUB_INTERSTITIAL_CLASS)) {
                handleMoPubKeywordsUpdate(adObj, adUnitCode, context);
            } else if (adObj.getClass() == getClassFromString(DFP_ADREQUEST_CLASS)) {
                handleDFPCustomTargetingUpdate(adObj, adUnitCode, context);
            }
        }
    }

    private static void markBidsSend(String adUnitCode) {
        BidManager.markBidsSend(adUnitCode);
        /*bidAdunitMap.containsKey(adUnitCode)){
            for (Object view : bidAdunitMap.get(adUnitCode)) {
                if(((AdUnitBidMap)view). == adView){

                }
            }
        }*/

    }
    private static void cleanBidsSend(String adUnitCode) {
        BidManager.cleanBidsSend(adUnitCode);
    }

    public static void mapBidToAdView(final Object adView, String adUnitCode){
        AdUnitBidMap map = new AdUnitBidMap(adView, adUnitCode);
        //Object listener = Class.forName("com.google.android.gms.ads.doubleclick.AppEventListener").newInstance();
        //Class<?> listClass = listener.getClass();
        Class<?> listClass = appListener.getClass();
        Object newList = null;
        try {
            newList = listClass.newInstance();
            Field fld = listClass.getField("adView");
            fld.set(newList, adView);
        }catch (Exception e){
            e.printStackTrace();
        }



        callMethodOnObject2(adView, "setAppEventListener", newList);
        if(!bidAdunitMap.containsKey(adUnitCode)){
            bidAdunitMap.put(adUnitCode, new ArrayList<Object>());
            bidAdunitMap.get(adUnitCode).add(map);
        }else if(!checkIfAdViewExists(bidAdunitMap.get(adUnitCode), adView)){
            bidAdunitMap.get(adUnitCode).add(map);
        }
    }

    private static boolean checkIfAdViewExists(List<Object> maps, Object adView){
        for (Object map: maps) {
            if(((AdUnitBidMap)map).adView == adView){
                return true;
            }
        }
        return false;
    }

    public static AdUnitBidMap getAdunitMapByAdView(Object adView){
        for ( Map.Entry<String, List<Object>> map : Prebid.bidAdunitMap.entrySet()) {
            for (Object view : map.getValue()) {
                if(((AdUnitBidMap)view).adView == adView){
                    return (AdUnitBidMap)view;
                }
            }
        }
        return  null;
    }


    public static void detachUsedBid(Object adObj) {
        if (adObj != null) {
            if (adObj.getClass() == getClassFromString(MOPUB_ADVIEW_CLASS)) {
                removeUsedKeywordsForMoPub(adObj);
            } else if (adObj.getClass() == getClassFromString(DFP_ADREQUEST_CLASS)) {
                removeUsedCustomTargetingForDFP(adObj);
            }
        }
    }

    public static void attachBidsWhenReady(final Object adObject, String adUnitCode, final OnAttachCompleteListener listener, int timeOut, final Context context) {
        BidManager.getKeywordsWhenReadyForAdUnit(adUnitCode, timeOut, new BidManager.BidReadyListener() {
            @Override
            public void onBidReady(String adUnitCode) {
                attachBids(adObject, adUnitCode, context);
                listener.onAttachComplete(adObject);
            }
        });
    }

    public static void attachBidsWhenReady(final Object adObject, final Object adView, String adUnitCode, final OnAttachCompleteListener listener, int timeOut, final Context context) {


        bidAdunitMap.remove(adUnitCode);
        //BidManager.clearBidMapForAdUnit(adUnitCode);
        cleanBidsSend(adUnitCode);

        mapBidToAdView(adView, adUnitCode);

        BidManager.requiresAuction(adUnitCode, context);//TODO: make not adunit dependant

        BidManager.getKeywordsWhenReadyForAdUnit(adUnitCode, timeOut, new BidManager.BidReadyListener() {
            @Override
            public void onBidReady(String adUnitCode) {
                LogUtil.d("Bids are ready");

                attachBids(adObject, adUnitCode, context);
                listener.onAttachComplete(adObject);
            }
        });
    }



    //endregion

    //region helper methods
    private static Class getClassFromString(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
        }
        return null;
    }

    private static Object callMethodOnObject(Object object, String methodName, Object... params) {
        try {
            int len = params.length;
            Class<?>[] classes = new Class[len];
            for (int i = 0; i < len; i++) {
                classes[i] = params[i].getClass();
            }
            Method method = object.getClass().getMethod(methodName, classes);
            return method.invoke(object, params);
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
    private static Object callMethodOnObject2(Object object, String methodName, Object... params) {
        try {
            int len = params.length;
            Class<?>[] classes = new Class[len];
            for (int i = 0; i < len; i++) {
                classes[i] = params[i].getClass().getInterfaces()[0];
            }
            Method method = object.getClass().getMethod(methodName, classes);
            return method.invoke(object, params);
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static final LinkedList<String> usedKeywordsList = new LinkedList<String>();

    private static void handleMoPubKeywordsUpdate(Object adViewObj, String adUnitCode, Context context) {
        ArrayList<Pair<String, String>> keywordPairs = BidManager.getKeywordsForAdUnit(adUnitCode, context);

        if (keywordPairs != null && !keywordPairs.isEmpty()) {
            StringBuilder keywords = new StringBuilder();
            for (Pair<String, String> p : keywordPairs) {
                keywords.append(p.first).append(":").append(p.second).append(",");
            }
            String prebidKeywords = keywords.toString();
            String adViewKeywords = (String) callMethodOnObject(adViewObj, "getKeywords");
            // retrieve keywords from mopub adview
            if (!TextUtils.isEmpty(adViewKeywords)) {
                adViewKeywords = prebidKeywords + adViewKeywords;
            } else {
                adViewKeywords = prebidKeywords;
            }
            // only set keywords if less than mopub query string limit
            if (adViewKeywords.length() <= kMoPubQueryStringLimit) {
                synchronized (usedKeywordsList) {
                    usedKeywordsList.add(prebidKeywords);
                }
                callMethodOnObject(adViewObj, "setKeywords", adViewKeywords);
            }
        }

    }

    private static void removeUsedKeywordsForMoPub(Object adViewObj) {
        String adViewKeywords = (String) callMethodOnObject(adViewObj, "getKeywords");
        if (!TextUtils.isEmpty(adViewKeywords) && !usedKeywordsList.isEmpty()) {
            // Copy used keywords to a temporary list to avoid concurrent modification
            // while iterating through the list
            LinkedList<String> tempUsedKeywords = new LinkedList<String>();
            for (String usedKeyword : usedKeywordsList) {
                if (!TextUtils.isEmpty(usedKeyword) && adViewKeywords.contains(usedKeyword)) {
                    adViewKeywords = adViewKeywords.replace(usedKeyword, "");
                    tempUsedKeywords.add(usedKeyword);
                }
            }
            callMethodOnObject(adViewObj, "setKeywords", adViewKeywords);

            for (String string : tempUsedKeywords) {
                synchronized (usedKeywordsList) {
                    usedKeywordsList.remove(string);
                }
            }

        }
    }

    private static final Set<String> usedKeywordKeys = new HashSet<String>();

    private static void handleDFPCustomTargetingUpdate(Object adRequestObj, String adUnitCode, Context context) {
        Bundle bundle = (Bundle) callMethodOnObject(adRequestObj, "getCustomTargeting");
        if (bundle != null) {
            ArrayList<Pair<String, String>> prebidKeywords = BidManager.getKeywordsForAdUnit(adUnitCode, context);

            if (prebidKeywords != null && !prebidKeywords.isEmpty()) {
                // retrieve keywords from mopub adview
                for (Pair<String, String> keywordPair : prebidKeywords) {
                    bundle.putString(keywordPair.first, keywordPair.second);
                    usedKeywordKeys.add(keywordPair.first);
                }
            }
        }
    }

    private static void removeUsedCustomTargetingForDFP(Object adRequestObj) {
        Bundle bundle = (Bundle) callMethodOnObject(adRequestObj, "getCustomTargeting");
        if (bundle != null) {
            for (String key : usedKeywordKeys) {
                bundle.remove(key);
            }
        }
    }
    //endregion


    public static String getAccountId() {
        return accountId;
    }

    /**
     * Get whether to use secure connection
     *
     * @return true if ad requests should be loaded over secure connection
     */
    public static boolean isSecureConnection() {
        return secureConnection;
    }

    /**
     * Set whether to use secure connection for ad requests
     *
     * @param secureConnection true to use secure connection
     */
    public static void shouldLoadOverSecureConnection(boolean secureConnection) {
        // Only enables overrides for MoPub, DFP should always load over secured connection
        //if (Prebid.adServer.equals(AdServer.MOPUB)) {
            Prebid.secureConnection = secureConnection;
        //}
    }

    public static void adUnitReceivedDefault(Object adView) {
        AdUnitBidMap bidmap = Prebid.getAdunitMapByAdView(adView);
        bidmap.isDefault = true;
    }

    public static void adUnitReceivedAppEvent(Object adView, String instruction, String prm) {
        if (instruction.equals("deliveryData")) {
            LogUtil.d("DPF-Banner", "onAppEvent" + instruction+":"+ prm);
            String[] serveData = prm.split("\\|");
            AdUnitBidMap bidmap = Prebid.getAdunitMapByAdView(adView);
            if(serveData.length==2) {
                bidmap.data.lineItemId = serveData[0];
                bidmap.data.creativeId = serveData[1];
            }
            //Prebid.gatherStats();
        }else if(instruction.equals("wonHB")){
            //TODO: match cache id on available bids, to determine the exact winner
            AdUnitBidMap bidmap = Prebid.getAdunitMapByAdView(adView);
            //bidmap.isWinner = true;

            Prebid.markWinner(bidmap.adUnitCode, prm);
        }
    }

    private static void trackStats(JSONObject stats){
        String url = null;
        switch (host){
            case ADSOLUTIONS:
                url = "https://tagmans3.adsolutions.com/log/";
                break;
            case ADSOLUTIONS_DEV:
                url = "http://192.168.0.45/log/";
                break;


        }
        int retryCnt = 0;
        try{
            if(url != null) {
                new StatsTracker(url, stats).execute();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void gatherStats(){
        JSONObject statsDict = new JSONObject();
        try {

            int Measuredwidth = 0;
            int Measuredheight = 0;
            Point size = new Point();
            WindowManager w = (WindowManager) ((Application)Prebid.context).getSystemService(Context.WINDOW_SERVICE);

            DisplayMetrics displayMetrics = new DisplayMetrics();
            w.getDefaultDisplay().getMetrics(displayMetrics);
            int height = displayMetrics.heightPixels;
            int width = displayMetrics.widthPixels;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                w.getDefaultDisplay().getRealMetrics(displayMetrics);
                height = displayMetrics.heightPixels;
                width = displayMetrics.widthPixels;
            }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)    {
                w.getDefaultDisplay().getSize(size);
                Measuredwidth = size.x;
                Measuredheight = size.y;




            }else{
                Display d = w.getDefaultDisplay();
                Measuredwidth = d.getWidth();
                Measuredheight = d.getHeight();
            }


            statsDict.put("client", accountId);
            statsDict.put("screenWidth", width);
            statsDict.put("screenHeight", height);
            statsDict.put("viewWidth", Measuredwidth);
            statsDict.put("viewHeight", Measuredheight);
            statsDict.put("language", "nl");
            statsDict.put("host", appName);
            statsDict.put("page", appPage);
            statsDict.put("proto", "https:");
            statsDict.put("timeToLoad", 0);
            statsDict.put("timeToPlacement", 0);
            statsDict.put("duration", 0);

            statsDict.put("placements", gatherPlacements());


            trackStats(statsDict);
        }catch (JSONException e){
            e.printStackTrace();
        }


    }
    private  static JSONArray gatherPlacements() throws JSONException{
        JSONArray placementDict = new JSONArray();
        int cnt = 0;
        for ( Map.Entry<String, List<Object>> map : Prebid.bidAdunitMap.entrySet()) {
            for (Object placementObj : map.getValue()) {
                AdUnitBidMap placement = (AdUnitBidMap) placementObj;
                placementDict.put(gatherSizes(placement, cnt++));
            }
        }
        return placementDict;
    }
    private static JSONObject gatherSizes(AdUnitBidMap placement, int cnt) throws JSONException{
        JSONObject sizesDict = new JSONObject();
        JSONArray sizeArr = new JSONArray();
        sizeArr.put(gatherSize(placement));
        sizesDict.put("sizes", sizeArr);

        return sizesDict;
    }
    private static JSONObject gatherSize(AdUnitBidMap placement) throws JSONException{
        JSONObject sizesDict = new JSONObject();
        JSONObject prebidDict = new JSONObject();
        JSONArray tiersList = new JSONArray();
        JSONObject tierDict = new JSONObject();
        JSONArray bidsList = new JSONArray();


        sizesDict.put("id", 0);
        sizesDict.put("isDefault", placement.isDefault);
        sizesDict.put("viaAdserver", true);
        sizesDict.put("active", true);
        sizesDict.put("prebid", prebidDict);
        prebidDict.put("tiers", tiersList);

        tiersList.put(tierDict);

        tierDict.put("id", 0);
        tierDict.put("bids", bidsList);

        ArrayList<BidResponse> bids = BidManager.getBidsForAdUnit(placement.adUnitCode);
        for (BidResponse bid : bids) {
            JSONObject jsonBid = gatherBid(bid);
            bidsList.put(jsonBid);
        }

        return sizesDict;
    }

    private static JSONObject gatherBid(BidResponse bid) throws  JSONException{
        JSONObject bidObj = new JSONObject();
        bidObj.put("bidder",bid.getBidderCode());
        bidObj.put( "won",  bid.getWinner());
        bidObj.put("cpm", Math.round(bid.getCpm()*1000));
        bidObj.put("origCPM", null);
        bidObj.put("time", bid.getResponseTime());
        bidObj.put("size",  bid.getSize());
        bidObj.put("state", bid.getStatusCode());//TODO:detect correct status //0= pending, 1= bid available, 2= no bid available, 3=bid time-out

        //bidObj.put("bidderTier",null);
        //bidObj.put("tierFloor", null);
        return bidObj;
    }
}
