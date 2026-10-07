package com.autopinger;

import android.content.Context;

public class PromoManager {

    // 🔐 HIDDEN CODES — UI ME NAHI DIKHENGE
    private static final String[][] PROMOS = {
            {"Z4XGAMING",   "1000", "Founder Gift"},
            {"WELCOME50",    "50",  "Welcome Bonus"},
            {"PROVIP",      "5000", "VIP Pass"},
            {"TELEGRAM",     "100", "Telegram Bonus"},
            {"AUTOPINGER",   "250", "Loyal User"},
            {"FIRST100",    "2000", "First 100 Users"},
    };

    public static class Result {
        public boolean valid; public int coins; public String label; public String error;
        public Result(boolean v, int c, String l, String e) { valid = v; coins = c; label = l; error = e; }
    }

    public static Result redeem(Context c, String input) {
        if (input == null || input.trim().isEmpty())
            return new Result(false, 0, null, "Khaali code");
        String code = input.trim().toUpperCase();
        for (String[] p : PROMOS) {
            if (p[0].equals(code)) {
                if (WalletManager.isPromoUsed(c, code))
                    return new Result(false, 0, null, "Already used!");
                int coins = Integer.parseInt(p[1]);
                WalletManager.addCoins(c, coins);
                WalletManager.markPromoUsed(c, code);
                return new Result(true, coins, p[2], null);
            }
        }
        return new Result(false, 0, null, "❌ Invalid code");
    }
}
