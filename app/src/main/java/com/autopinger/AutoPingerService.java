package com.autopinger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class AutoPingerService extends Service {
    public static final String CHANNEL_ID = "autopinger_channel";
    public static final int NOTIF_ID = 1001;

    public static volatile boolean isRunning = false;
    public static volatile int pingCount = 0;
    public static volatile int successCount = 0;
    public static volatile String lastStatus = "Starting...";
    public static volatile long nextPingTime = 0;
    public static volatile long expiryTime = 0;
    public static volatile String websiteName = "—";
    public static volatile String websiteTitle = "—";

    Handler handler = new Handler(Looper.getMainLooper());
    ConnectivityManager cm;
    String currentUrl = "";
    long INTERVAL_MS = 5 * 60 * 1000L;
    long DURATION_MS = 0;

    Runnable pingTask = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            if (expiryTime > 0 && System.currentTimeMillis() >= expiryTime) {
                stopSelfPinging("⏰ Duration khatam");
                return;
            }
            if (!isInternetAvailable()) {
                lastStatus = "📴 Internet off — waiting...";
                updateNotification();
                handler.postDelayed(this, 10000);
                return;
            }
            lastStatus = "🌐 Pinging...";
            updateNotification();
            doPing();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            currentUrl = intent.getStringExtra("url");
            INTERVAL_MS = intent.getLongExtra("interval", 5 * 60 * 1000L);
            DURATION_MS = intent.getLongExtra("duration", 0);

            SharedPreferences prefs = getSharedPreferences("autopinger", MODE_PRIVATE);
            String tgToken = prefs.getString("tg_token", "");
            String tgChat = prefs.getString("tg_chat", "");

            if (!isRunning) {
                isRunning = true;
                pingCount = 0;
                successCount = 0;
                expiryTime = DURATION_MS > 0 ? System.currentTimeMillis() + DURATION_MS : 0;
                startForeground(NOTIF_ID, buildNotification());
                handler.removeCallbacks(pingTask);
                handler.post(pingTask);
                sendTelegram(tgToken, tgChat, "🚀 AutoPinger Started\n🌐 " + currentUrl);
            }
        }
        return START_STICKY;
    }

    private void doPing() {
        new Thread(() -> {
            String title = "—", siteName = "—";
            boolean ok = false;
            int code = 0;
            try {
                URL u = new URL(currentUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AutoPinger/1.0");
                conn.connect();
                code = conn.getResponseCode();

                StringBuilder sb = new StringBuilder();
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line; int lines = 0;
                    while ((line = br.readLine()) != null && lines < 80) { sb.append(line); lines++; }
                    br.close();
                } catch (Exception ignored) {}
                conn.disconnect();

                String html = sb.toString();
                int s = html.indexOf("<title>"), e = html.indexOf("</title>");
                if (s >= 0 && e > s) title = html.substring(s + 7, e).trim();
                siteName = u.getHost().replace("www.", "");
                if (title.length() > 60) title = title.substring(0, 60) + "...";

                ok = code >= 200 && code < 400;
            } catch (Exception ignored) {}

            final boolean success = ok;
            final String t = title;
            final String sn = siteName;
            final int c = code;

            handler.post(() -> {
                pingCount++;
                if (success) successCount++;
                websiteName = sn;
                websiteTitle = t;
                lastStatus = success ? "✅ Ping #" + pingCount + " OK" : "❌ Ping #" + pingCount + " Fail";

                updateNotification();

                SharedPreferences prefs = getSharedPreferences("autopinger", MODE_PRIVATE);
                String tgToken = prefs.getString("tg_token", "");
                String tgChat = prefs.getString("tg_chat", "");
                if (pingCount % 5 == 0 || !success) {
                    sendTelegram(tgToken, tgChat,
                            (success ? "✅" : "❌") + " Ping #" + pingCount +
                            "\n🌐 " + sn + "\n📊 HTTP " + c + "\n📄 " + t +
                            "\n📈 Uptime: " + (pingCount > 0 ? (successCount * 100 / pingCount) : 0) + "%");
                }

                if (!isRunning) return;
                if (expiryTime > 0 && System.currentTimeMillis() >= expiryTime) {
                    stopSelfPinging("⏰ Duration khatam");
                    return;
                }
                nextPingTime = System.currentTimeMillis() + INTERVAL_MS;
                updateNotification();
                handler.postDelayed(pingTask, INTERVAL_MS);
            });
        }).start();
    }

    private void stopSelfPinging(String reason) {
        isRunning = false;
        lastStatus = reason;
        handler.removeCallbacks(pingTask);

        SharedPreferences prefs = getSharedPreferences("autopinger", MODE_PRIVATE);
        String tgToken = prefs.getString("tg_token", "");
        String tgChat = prefs.getString("tg_chat", "");
        sendTelegram(tgToken, tgChat, "⏹️ AutoPinger Stopped\n" + reason +
                "\n📊 Total: " + pingCount + " | ✅ " + successCount);

        stopForeground(true);
        stopSelf();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, piFlags);

        int pct = pingCount > 0 ? (successCount * 100 / pingCount) : 0;
        String nextStr = "—";
        if (nextPingTime > 0) {
            long left = Math.max(0, nextPingTime - System.currentTimeMillis());
            nextStr = (left / 60000L) + "m " + ((left % 60000L) / 1000L) + "s";
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚀 AutoPinger — " + lastStatus)
                .setContentText("🌐 " + websiteName + " | Uptime: " + pct +
                        "% | Pings: " + pingCount + " | Next: " + nextStr)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "AutoPinger Service", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Website pinging in background");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private boolean isInternetAvailable() {
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(n);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void sendTelegram(final String token, final String chat, final String message) {
        if (token == null || token.isEmpty() || chat == null || chat.isEmpty()) return;
        new Thread(() -> {
            try {
                String apiUrl = "https://api.telegram.org/bot" + token + "/sendMessage";
                String post = "chat_id=" + URLEncoder.encode(chat, "UTF-8") +
                        "&text=" + URLEncoder.encode(message, "UTF-8");
                HttpURLConnection c = (HttpURLConnection) new URL(apiUrl).openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                OutputStream os = c.getOutputStream();
                os.write(post.getBytes("UTF-8"));
                os.flush(); os.close();
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacks(pingTask);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
