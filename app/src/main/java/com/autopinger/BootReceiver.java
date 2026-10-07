package com.autopinger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : "";
        if (action == null) return;
        if (!action.equals(Intent.ACTION_BOOT_COMPLETED)
                && !action.equals("android.intent.action.QUICKBOOT_POWERON")
                && !action.equals("com.htc.intent.action.QUICKBOOT_POWERON")) return;

        SharedPreferences prefs = context.getSharedPreferences("autopinger", Context.MODE_PRIVATE);
        boolean wasRunning = prefs.getBoolean("was_running", false);
        if (!wasRunning) return;

        String url = prefs.getString("url", "");
        long intervalMs = prefs.getLong("interval_ms", 5 * 60 * 1000L);
        long durationMs = prefs.getLong("duration_ms", 0);
        long savedExpiry = prefs.getLong("expiry_time", 0);

        if (url.isEmpty()) return;

        long remaining = 0;
        if (savedExpiry > 0) {
            remaining = savedExpiry - System.currentTimeMillis();
            if (remaining <= 0) {
                prefs.edit().putBoolean("was_running", false).apply();
                return;
            }
        }

        Intent svc = new Intent(context, AutoPingerService.class);
        svc.putExtra("url", url);
        svc.putExtra("interval", intervalMs);
        svc.putExtra("duration", remaining > 0 ? remaining : durationMs);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
