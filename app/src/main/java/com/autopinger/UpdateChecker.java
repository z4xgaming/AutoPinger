package com.autopinger;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private static final String API = "https://api.github.com/repos/z4xgaming/AutoPinger/releases/latest";

    public interface CB { void result(boolean avail, String version, String url, String log); }

    public static void check(Context ctx, final CB cb) {
        new Thread(() -> {
            boolean avail = false;
            String latest = null, url = null, log = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setRequestProperty("User-Agent", "AutoPinger");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    String ln;
                    while ((ln = br.readLine()) != null) sb.append(ln);
                    br.close();
                    JSONObject j = new JSONObject(sb.toString());
                    latest = j.optString("tag_name", "").replace("v", "");
                    log = j.optString("body", "");
                    JSONArray assets = j.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject a = assets.getJSONObject(i);
                            if (a.optString("name", "").endsWith(".apk")) {
                                url = a.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }
                    String cur = "1.0";
                    try {
                        PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                        cur = pi.versionName;
                    } catch (Exception ignored) {}
                    if (latest != null && url != null) {
                        avail = compare(latest, cur) > 0;
                    }
                }
                c.disconnect();
            } catch (Exception ignored) {}

            final boolean fa = avail;
            final String fl = latest, fu = url, flg = log;
            new Handler(Looper.getMainLooper()).post(() -> cb.result(fa, fl, fu, flg));
        }).start();
    }

    private static int compare(String a, String b) {
        try {
            String[] x = a.split("\\."), y = b.split("\\.");
            int n = Math.max(x.length, y.length);
            for (int i = 0; i < n; i++) {
                int xi = i < x.length ? Integer.parseInt(x[i].replaceAll("[^0-9]", "")) : 0;
                int yi = i < y.length ? Integer.parseInt(y[i].replaceAll("[^0-9]", "")) : 0;
                if (xi != yi) return xi - yi;
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
