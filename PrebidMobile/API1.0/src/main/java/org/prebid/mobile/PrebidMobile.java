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

package org.prebid.mobile;

import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PrebidMobile {

    private static  long lastGatherStats = System.currentTimeMillis();
    private static Set<AdUnit> adUnits = new HashSet<>();
    private static Map<String, List<Object>> bidAdunitMap = new HashMap<String, List<Object>>();
    private static ConcurrentHashMap<String, ArrayList<BidResponse>> bidMap = new ConcurrentHashMap<String, ArrayList<BidResponse>>();

    public static final int TIMEOUT_MILLIS = 2_000;

    static int timeoutMillis = TIMEOUT_MILLIS; // by default use 2000 milliseconds as timeout
    static boolean timeoutMillisUpdated = false;

    private PrebidMobile() {
    }

    public static void registerAdUnit(AdUnit adUnit) {
        AdUnit existingAdUnit = PrebidMobile.getAdUnitByCode(adUnit.getCode());
        if (existingAdUnit == null) {
            adUnits.add(adUnit);
        }
    }

    public static void setBidResponsesForAdUnit(AdUnit adUnit, ArrayList<BidResponse> bidResponses) {
        bidMap.remove(adUnit.getCode());
        bidMap.put(adUnit.getCode(), bidResponses);
    }

    private static String accountId = "";

    public static void setPrebidServerAccountId(String accountId) {
        PrebidMobile.accountId = accountId;
    }

    public static String getPrebidServerAccountId() {
        return accountId;
    }

    private static Object appListener;

    public static void setAppListener(Object appListener) {
        PrebidMobile.appListener = appListener;
    }

    public static void adUnitReceivedAppEvent(Object adView, String instruction, String prm) {

        if (instruction.equals("deliveryData")) {
            LogUtil.d("DPF-Banner", "onAppEvent" + instruction+":"+ prm);
            String[] serveData = prm.split("\\|");
            AdUnitBidMap bidmap = getAdunitMapByAdView(adView);
            if(serveData.length==2) {
                bidmap.data.lineItemId = serveData[0];
                bidmap.data.creativeId = serveData[1];
            }

        }else if(instruction.equals("wonHB")){
            AdUnitBidMap bidmap = getAdunitMapByAdView(adView);
            LogUtil.d("wonHB!!!");
            LogUtil.d(prm);

            markWinner(bidmap.adUnitCode, prm);

        }
    }

    public static ArrayList<BidResponse> getBidsForAdUnit(String adUnitCode) {
        if(bidMap.containsKey(adUnitCode)){
            return bidMap.get(adUnitCode);
        }
        return new ArrayList<BidResponse>();
        //return  null;
    }

    public static void markWinner(String adUnitCode, String creativeId) {
        ArrayList<BidResponse> bids = getBidsForAdUnit(adUnitCode);
        for (BidResponse bid : bids) {
            if(bid.getCreative().equals(creativeId)){
                bid.setWinner();
            }
        }
    }

    private static String appName;

    public static void setAppName(String appName) {
        PrebidMobile.appName = appName;
    }

    private static String appPage;

    public static void setAppPage(String appPage) {
        PrebidMobile.appPage = appPage;
    }

    public static void initCacheManager() {
        CacheManager.init(PrebidMobile.getApplicationContext());
    }

    public static void bindNativeListener(final Object adView) {

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

    }

    public static void mapBidToAdView(final Object adView, String adUnitCode){

        bidAdunitMap.remove(adUnitCode);

        AdUnitBidMap map = new AdUnitBidMap(adView, adUnitCode);
        //Object listener = Class.forName("com.google.android.gms.ads.doubleclick.AppEventListener").newInstance();
        //Class<?> listClass = listener.getClass();

        try {
            PrebidMobile.bindNativeListener(adView);
        } catch (Exception e) {
            e.printStackTrace();
        }



        if(!bidAdunitMap.containsKey(adUnitCode)){
            bidAdunitMap.put(adUnitCode, new ArrayList<Object>());
            bidAdunitMap.get(adUnitCode).add(map);
        }else if(!checkIfAdViewExists(bidAdunitMap.get(adUnitCode), adView)){
            bidAdunitMap.get(adUnitCode).add(map);
        }
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

    private static boolean checkIfAdViewExists(List<Object> maps, Object adView){
        for (Object map: maps) {
            if(((AdUnitBidMap)map).adView == adView){
                return true;
            }
        }
        return false;
    }


    private static Host host = Host.CUSTOM;

    public static void setPrebidServerHost(Host host) {
        PrebidMobile.host = host;
        timeoutMillisUpdated = false; // each time a developer sets a new Host for the SDK, we should re-calculate the time out millis
        timeoutMillis = TIMEOUT_MILLIS;
    }


    public static Host getPrebidServerHost() {
        return host;
    }

    private static boolean shareGeoLocation = false;

    public static void setShareGeoLocation(boolean share) {
        PrebidMobile.shareGeoLocation = share;
    }

    public static boolean isShareGeoLocation() {
        return shareGeoLocation;
    }

    private static WeakReference<Context> applicationContextWeak;

    public static void setApplicationContext(Context context) {
        applicationContextWeak = new WeakReference<Context>(context);
    }

    public static Context getApplicationContext() {
        if (applicationContextWeak != null) {
            return applicationContextWeak.get();
        }
        return null;
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

    public static void markAdUnitLoaded(Object adView) {
        AdUnitBidMap adunitmap = getAdunitMapByAdView(adView);
        adunitmap.isServerUpdated = false;
        AdUnit adUnit = getAdUnitByCode(adunitmap.adUnitCode);
        adUnit.stopLoadTime = System.currentTimeMillis();

    }

    public static void adUnitReceivedDefault(Object adView) {
        AdUnitBidMap bidmap = getAdunitMapByAdView(adView);
        bidmap.isDefault = true;
    }

    static AdUnit getAdUnitByCode(String code) {
        for (AdUnit adUnit : adUnits) {
            if (adUnit.getCode() != null && adUnit.getCode().equals(code)) {
                return adUnit;
            }
        }
        return null;
    }

    static AdUnitBidMap getAdunitMapByAdView(Object adView){
        for ( Map.Entry<String, List<Object>> map : PrebidMobile.bidAdunitMap.entrySet()) {
            for (Object view : map.getValue()) {
                if(((AdUnitBidMap)view).adView == adView){
                    return (AdUnitBidMap) view;
                }
            }
        }
        return  null;
    }

    private static JSONObject gatherSizes(AdUnitBidMap placement) throws JSONException{
        JSONObject sizesDict = new JSONObject();
        JSONArray sizeArr = new JSONArray();
        sizeArr.put(gatherSize(placement));
        sizesDict.put("sizes", sizeArr);

        return sizesDict;
    }
    private static JSONObject gatherSize(AdUnitBidMap placement) throws JSONException{
        JSONObject sizeDict = new JSONObject();
        JSONObject prebidDict = new JSONObject();
        JSONArray tiersList = new JSONArray();
        JSONObject tierDict = new JSONObject();
        JSONArray bidsList = new JSONArray();
        JSONObject adserverDict = new JSONObject();
        JSONObject deliveryDict = new JSONObject();


        AdUnit adunit = getAdUnitByCode(placement.adUnitCode);


        sizeDict.put("id", 0);
        sizeDict.put("isDefault", placement.isDefault);
        sizeDict.put("viaAdserver", true);
        sizeDict.put("active", true);
        sizeDict.put("timeToLoad", adunit.getTimeToLoad());

        String adunitId = (String) callMethodOnObject(placement.adView, "getAdUnitId");
        adunitId = adunitId.replaceFirst("^/","");
        //if(adunitId.charAt(0) != '/'){
        //adunitId = "/"+adunitId;
        //}
        adserverDict.put("name","DFP");
        adserverDict.put("id", adunitId);

        deliveryDict.put("lineitemId", placement.data.lineItemId);
        deliveryDict.put("creativeId", placement.data.creativeId);

        adserverDict.put("delivery", deliveryDict);

        sizeDict.put("adserver", adserverDict);


        sizeDict.put("prebid", prebidDict);
        prebidDict.put("tiers", tiersList);

        tiersList.put(tierDict);

        tierDict.put("id", 0);
        tierDict.put("bids", bidsList);

        ArrayList<BidResponse> bids = getBidsForAdUnit(placement.adUnitCode);
        for (BidResponse bid : bids) {
            JSONObject jsonBid = gatherBid(bid);
            bidsList.put(jsonBid);
        }

        return sizeDict;
    }

    private static JSONObject gatherBid(BidResponse bid) throws  JSONException{
        JSONObject bidObj = new JSONObject();
        bidObj.put("bidder",bid.getBidderCode());
        bidObj.put( "won",  bid.getWinner());
        bidObj.put("cpm", Math.round(bid.getCpm()*1000));
        bidObj.put("origCPM", null);
        bidObj.put("time", bid.getResponseTime());//TODO: should this include the roundtrip to the prebid server? or should this be an additional metric
        bidObj.put("size",  bid.getSize());
        bidObj.put("state", bid.getStatusCode());//TODO:detect correct status //0= pending, 1= bid available, 2= no bid available, 3=bid time-out
        if(bid.dealId != null && !bid.dealId.isEmpty()){
            bidObj.put("dealId", bid.dealId);
        }

        //bidObj.put("bidderTier",null);
        //bidObj.put("tierFloor", null);
        return bidObj;
    }

    private  static JSONArray gatherPlacements() throws JSONException{
        JSONArray placementDict = new JSONArray();
        int cnt = 0;
        for ( Map.Entry<String, List<Object>> map : bidAdunitMap.entrySet()) {
            for (Object placementObj : map.getValue()) {
                AdUnitBidMap placement = (AdUnitBidMap) placementObj;
                if (!placement.isServerUpdated) {
                    placementDict.put(gatherSizes(placement));
                }
                placement.isServerUpdated = true;
            }
        }
        return placementDict;
    }


    public static void gatherStats(){
        JSONObject statsDict = new JSONObject();
        try {

            int Measuredwidth = 0;
            int Measuredheight = 0;
            Point size = new Point();
            WindowManager w = (WindowManager) (PrebidMobile.getApplicationContext()).getSystemService(Context.WINDOW_SERVICE);

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

            String currentLang = Locale.getDefault().getLanguage();

            statsDict.put("client", accountId);
            statsDict.put("screenWidth", width);
            statsDict.put("screenHeight", height);
            statsDict.put("viewWidth", Measuredwidth);
            statsDict.put("viewHeight", Measuredheight);
            statsDict.put("language", currentLang);
            statsDict.put("host", appName);
            statsDict.put("page", appPage);
            statsDict.put("proto", "https:");
            statsDict.put("timeToLoad", 0);
            statsDict.put("timeToPlacement", 0);
            statsDict.put("duration", System.currentTimeMillis() - PrebidMobile.lastGatherStats);

            statsDict.put("placements", gatherPlacements());

            LogUtil.d("statsDict is " + statsDict.toString());

            PrebidMobile.lastGatherStats = System.currentTimeMillis();

            trackStats(statsDict);
        }catch (JSONException e){
            e.printStackTrace();
        }


    }


}
