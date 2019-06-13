package org.prebid.mobile;

import android.os.AsyncTask;

import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class StatsTracker extends AsyncTask<Void, Void, Void> {
    public  String url;
    public JSONObject stats;
    public StatsTracker(String url, JSONObject stats){
        super();
        this.url = url;
        this.stats = stats;
    }
    @Override
    protected Void doInBackground(Void... voids) {
        try {
            URL urlO = new URL(this.url);

            HttpURLConnection conn = (HttpURLConnection) urlO.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);

            // Add post data
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
            wr.write(this.stats.toString());
            wr.flush();

            // Start the connection
            conn.connect();

            // Read request response
            int httpResult = conn.getResponseCode();
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
