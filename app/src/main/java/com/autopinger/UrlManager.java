package com.autopinger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UrlManager {

    public static class Site {
        public String url;
        public String name;
        public boolean live;
        public boolean active;
        public int pings;
        public int success;
        public int code;
        public long responseMs;
        public long lastCheckTime;
        public long totalUptimeMs;

        public Site(String url) {
            this.url = url;
            this.name = url.replaceAll("^https?://", "").replaceAll("/.*$", "");
            this.live = false;
            this.active = true;
            this.pings = 0;
            this.success = 0;
            this.code = 0;
            this.responseMs = 0;
            this.lastCheckTime = 0;
            this.totalUptimeMs = 0;
        }

        public int getUptimePercent() {
            return pings > 0 ? (success * 100 / pings) : 0;
        }
    }

    public static List<Site> loadAll(Context c) {
        List<Site> list = new ArrayList<>();
        String raw = c.getSharedPreferences("autopinger", Context.MODE_PRIVATE)
                .getString("sites_json", "");
        if (raw == null || raw.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Site s = new Site(o.optString("url", ""));
                s.name = o.optString("name", s.name);
                s.live = o.optBoolean("live", false);
                s.active = o.optBoolean("active", true);
                s.pings = o.optInt("pings", 0);
                s.success = o.optInt("success", 0);
                s.code = o.optInt("code", 0);
                s.responseMs = o.optLong("responseMs", 0);
                s.lastCheckTime = o.optLong("lastCheckTime", 0);
                s.totalUptimeMs = o.optLong("totalUptimeMs", 0);
                list.add(s);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static List<Site> loadActive(Context c) {
        List<Site> out = new ArrayList<>();
        for (Site s : loadAll(c)) if (s.active) out.add(s);
        return out;
    }

    public static List<Site> loadHistory(Context c) {
        List<Site> out = new ArrayList<>();
        for (Site s : loadAll(c)) if (!s.active) out.add(s);
        return out;
    }

    public static void save(Context c, List<Site> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Site s : list) {
                JSONObject o = new JSONObject();
                o.put("url", s.url);
                o.put("name", s.name);
                o.put("live", s.live);
                o.put("active", s.active);
                o.put("pings", s.pings);
                o.put("success", s.success);
                o.put("code", s.code);
                o.put("responseMs", s.responseMs);
                o.put("lastCheckTime", s.lastCheckTime);
                o.put("totalUptimeMs", s.totalUptimeMs);
                arr.put(o);
            }
            c.getSharedPreferences("autopinger", Context.MODE_PRIVATE)
                    .edit().putString("sites_json", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void add(Context c, String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        List<Site> list = loadAll(c);
        for (Site s : list) if (s.url.equals(url)) return;
        list.add(new Site(url));
        save(c, list);
    }

    public static void remove(Context c, String url) {
        List<Site> list = loadAll(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).url.equals(url)) list.remove(i);
        }
        save(c, list);
    }

    public static void moveToHistory(Context c, String url) {
        List<Site> list = loadAll(c);
        for (Site s : list) {
            if (s.url.equals(url)) {
                s.active = false;
                s.live = false;
                break;
            }
        }
        save(c, list);
    }

    public static void moveToActive(Context c, String url) {
        List<Site> list = loadAll(c);
        for (Site s : list) {
            if (s.url.equals(url)) {
                s.active = true;
                break;
            }
        }
        save(c, list);
    }

    public static void clearHistory(Context c) {
        List<Site> list = loadAll(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (!list.get(i).active) list.remove(i);
        }
        save(c, list);
    }

    public static void updateStats(Context c, String url, boolean live, int code, long ms) {
        List<Site> list = loadAll(c);
        for (Site s : list) {
            if (s.url.equals(url)) {
                s.live = live;
                s.code = code;
                s.responseMs = ms;
                s.pings++;
                if (live) s.success++;
                s.lastCheckTime = System.currentTimeMillis();
                if (live) s.totalUptimeMs += ms;
                break;
            }
        }
        save(c, list);
    }
}
