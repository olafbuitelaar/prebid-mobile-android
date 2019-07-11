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
package org.prebid.mobile;

import android.text.TextUtils;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Class used to form bid response from response data.
 * It contains bid information like bidderCode, creative, CPM, etc.
 * BidManager Module will use the bid information to create custom keywords to pass to other ad servers.
 */
public class BidResponse {
    //region Class Variables
    private static final long DEFAULT_EXPIRY = 5 * 60 * 1000;
    public String dealId;
    public JSONObject targetingKeywords;
    private JSONObject bidJSON;
    private long expiryTime = DEFAULT_EXPIRY;
    private Double cpm;
    private String creative;
    private String format;
    private String bidderCode;
    private int responseCode = 1;
    private int responseTime;
    private int width;
    private int height;
    private long createdTime;
    private boolean sendToAdserver=false;

    private ArrayList<Pair<String, String>> customKeywords = new ArrayList<Pair<String, String>>();
    private boolean won;
    //endregion

    //region Constructor
    public BidResponse(JSONObject jsonObject) throws JSONException {
        this.bidJSON = jsonObject;
        this.createdTime = System.currentTimeMillis();
        this.cpm = jsonObject.getDouble("price");
        this.creative = generateCacheId();

        this.targetingKeywords = jsonObject.getJSONObject("ext").getJSONObject("prebid").getJSONObject("targeting");
        String format = "html";
        if(this.targetingKeywords.has("hb_creative_loadtype")){
            format = this.targetingKeywords.getString("hb_creative_loadtype");
        }

        setFormat(format);
        setDimensions(jsonObject.getInt("w"), jsonObject.getInt("h"));
    }



    //endregion

    //region Public APIs

    /**
     * Get the CPM bid price of the response.
     *
     * @return bid price in CPM
     */
    public Double getCpm() {
        return cpm;
    }

    /**
     * Get the url that can return a creative.
     *
     * @return creative url
     */
    public String getCreative() {
        return creative;
    }

    /**
     * Get the custom keywords for customized BidManager targeting of Line items.
     *
     * @return an ArrayList of key value pairs
     */

    public ArrayList<Pair<String, String>> getCustomKeywords() {
        return customKeywords;
    }

    /**
     * Add a custom keyword for customized BidManager targeting of Line items.
     *
     * @param key   The key to add; this cannot be null or empty.
     * @param value The value to add; this cannot be null or empty.
     */
    public synchronized void addCustomKeyword(String key, String value) {
        if (TextUtils.isEmpty(key) || value == null)
            return;
        this.customKeywords.add(new Pair<String, String>(key, value));
    }

    /**
     * Set the expiration time. If method not used, will use the default value.
     *
     * @param expiryTime in milliseconds
     */
    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }

    /**
     * For BidManager Module to check if the bid response is still valid.
     *
     * @return false if the bid has been around more then expiration time (in milliseconds)
     */
    public boolean isExpired() {
        return ((System.currentTimeMillis() - this.createdTime) >= expiryTime);
    }

    /**
     * Set the name of the bidder that responded this bid
     *
     * @param bidderCode bidder's name
     */
    public void setBidderCode(String bidderCode) {
        this.bidderCode = bidderCode;
    }
    //endregion


    //region Package only helper methods
    long getExpiryTime() {
        return expiryTime;
    }


    public String getBidderCode() {
        return bidderCode;
    }

    public int getResponseTime(){return responseTime;};
    public void setResponseTime(int responseTime){this.responseTime=responseTime;}
    public void setDimensions(int width, int height){
        this.width = width;
        this.height = height;
    }
    public String getSize(){
        return this.width+"x"+this.height;
    }

    @Override
    public String toString() {
        return bidJSON.toString();
    }

    public void setWinner() {
        this.won = true;
    }
    public boolean getWinner(){
        return this.won;
    }

    public void setSendToAdserver() {
        this.sendToAdserver = true;
    }
    public boolean getSendToAndserver(){
        return this.sendToAdserver;
    }

    public int getStatusCode() {
        return responseCode;//TODO: figure out how to determine this! it doesn't seem present in the server response..
    }
    public void setStatusCode(int code){
        responseCode = code;
    }

    public String getFormat(){
        return format;
    }

    public void setFormat(String format){
        this.format = format;
    }

    public static String generateCacheId() {
        return "Prebid_" + StringUtils.randomLowercaseAlphabetic(8) + "_" + String.valueOf(System.currentTimeMillis());
    }

    public static Comparator<BidResponse> compareBids = new Comparator<BidResponse>() {
        public int compare(BidResponse r1, BidResponse r2) {

            double cpm1 = r1.getCpm();
            double cpm2 = r2.getCpm();

            if(cpm1>cpm2){
                return -1;
            }else if(cpm1<cpm2){
                return 1;
            }
            return 0;
        }
    };

    public static HashMap<String, String> getWinnerKeywords(List<BidResponse> bidResponses) {
        Collections.sort(bidResponses, compareBids);
        HashMap<String, String> keywordsPairs = new HashMap<>();
        BidResponse winner = bidResponses.get(0);
        if (winner != null && winner.getCpm() > 0) {
            String prefix = "pb_";
            keywordsPairs.put(prefix + "winner", winner.getBidderCode());
            keywordsPairs.put(prefix + "cpm", "" + Math.round(winner.getCpm() * 100));
            keywordsPairs.put(prefix + "size", winner.getSize());

            if(winner.dealId != null && !winner.dealId.isEmpty()){
                keywordsPairs.put(prefix + "deal", winner.dealId);
            }

            keywordsPairs.put("hb_size", winner.getSize());
            keywordsPairs.put("hb_env", "mobile-app");
            keywordsPairs.put("hb_format", "html");


            for (Pair<String, String> keyword : winner.getCustomKeywords()) {
                keywordsPairs.put(keyword.first, keyword.second);
            }

            keywordsPairs.remove("hb_cache_id");
            keywordsPairs.put("hb_cache_id", winner.getCreative());
        }

        return keywordsPairs;

    }


    //endregion
}
