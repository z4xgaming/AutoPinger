package com.autopinger;

import android.content.Context;
import android.content.SharedPreferences;

public class WalletManager {
    private static final String PREF = "wallet";
    private static final String K_COINS = "coins";
    private static final String K_DAILY = "last_daily";
    private static final String K_SCRATCH = "last_scratch";
    private static final String K_STREAK = "streak";
    private static final String K_TOTAL = "total_earned";
    private static final String K_PROMOS = "used_promos";

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static int getCoins(Context c) { return p(c).getInt(K_COINS, 0); }
    public static int getTotalEarned(Context c) { return p(c).getInt(K_TOTAL, 0); }

    public static void addCoins(Context c, int amt) {
        SharedPreferences s = p(c);
        s.edit().putInt(K_COINS, s.getInt(K_COINS, 0) + amt)
                .putInt(K_TOTAL, s.getInt(K_TOTAL, 0) + amt).apply();
    }

    public static boolean spendCoins(Context c, int amt) {
        SharedPreferences s = p(c);
        int cur = s.getInt(K_COINS, 0);
        if (cur < amt) return false;
        s.edit().putInt(K_COINS, cur - amt).apply();
        return true;
    }

    public static boolean canClaimDaily(Context c) {
        return System.currentTimeMillis() - p(c).getLong(K_DAILY, 0) >= 24L * 3600 * 1000;
    }

    public static long dailyNextTime(Context c) {
        return p(c).getLong(K_DAILY, 0) + 24L * 3600 * 1000;
    }

    public static void setDailyClaimed(Context c) {
        SharedPreferences s = p(c);
        long last = s.getLong(K_DAILY, 0);
        long now = System.currentTimeMillis();
        int streak = s.getInt(K_STREAK, 0);
        if (last > 0 && now - last <= 48L * 3600 * 1000) streak++;
        else streak = 1;
        s.edit().putLong(K_DAILY, now).putInt(K_STREAK, streak).apply();
    }

    public static int getStreak(Context c) {
        SharedPreferences s = p(c);
        long last = s.getLong(K_DAILY, 0);
        if (last > 0 && System.currentTimeMillis() - last > 48L * 3600 * 1000) {
            s.edit().putInt(K_STREAK, 0).apply();
            return 0;
        }
        return s.getInt(K_STREAK, 0);
    }

    public static boolean canClaimScratch(Context c) {
        return System.currentTimeMillis() - p(c).getLong(K_SCRATCH, 0) >= 4L * 3600 * 1000;
    }

    public static long scratchNextTime(Context c) {
        return p(c).getLong(K_SCRATCH, 0) + 4L * 3600 * 1000;
    }

    public static void setScratchClaimed(Context c) {
        p(c).edit().putLong(K_SCRATCH, System.currentTimeMillis()).apply();
    }

    public static boolean isPromoUsed(Context c, String code) {
        String u = p(c).getString(K_PROMOS, "");
        if (u == null || u.isEmpty()) return false;
        for (String s : u.split("\\|")) if (s.equals(code)) return true;
        return false;
    }

    public static void markPromoUsed(Context c, String code) {
        SharedPreferences s = p(c);
        String u = s.getString(K_PROMOS, "");
        if (u == null || u.isEmpty()) u = code;
        else u = u + "|" + code;
        s.edit().putString(K_PROMOS, u).apply();
    }
}
