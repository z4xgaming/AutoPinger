package com.autopinger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPingerService extends Service {
    public static final String CHANNEL_ID = "ap_silent";
    public static final int NOTIF_ID = 1001;

    public static volatile boolean isRunning = false;
    public static volatile int pingCount = 0;
    public static volatile int successCount = 0;
    public static volatile String lastStatus = "Starting...";
    public static volatile long nextPingTime = 0;
    public static volatile long expiryTime = 0;
    public static volatile boolean batterySaverMode = true;

    public static volatile String siteName = "—";
    public static volatile String siteTitle = "—";
    public static volatile String siteDescription = "—";
    public static volatile String siteAuthor = "—";
    public static volatile String siteKeywords = "—";
    public static volatile String siteServer = "—";
    public static volatile String siteIp = "—";
    public static volatile String siteProtocol = "—";
    public static volatile String siteContentType = "—";
    public static volatile String siteH1 = "—";
    public static volatile long siteResponseMs = 0;
    public static volatile int siteCode = 0;
    public static volatile long siteContentLength = 0;

    Handler handler = new Handler(Looper.getMainLooper());
    ConnectivityManager cm;
    PowerManager.WakeLock wakeLock;
    String currentUrl = "";
    long BASE_INTERVAL_MS = 5 * 60 * 1000L;
    long INTERVAL_MS = 5 * 60 * 1000L;
    long DURATION_MS = 0;
    long consecutiveFails = 0;

    Runnable pingTask = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            if (expiryTime > 0 && System.currentTimeMillis() >= expiryTime) {
                stopSelfPinging("⏰ Duration khatam");
                return;
            }
            if (!isInternetAvailable()) {
                lastStatus = "Internet off — waiting...";
                updateNotification();
                handler.postDelayed(this, 30000);
                return;
            }
            lastStatus = "Pinging...";
            updateNotification();
            doPing();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        createChannel();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoPinger:ping");
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            currentUrl = intent.getStringExtra("url");
            BASE_INTERVAL_MS = intent.getLongExtra("interval", 5 * 60 * 1000L);
            INTERVAL_MS = BASE_INTERVAL_MS;
            DURATION_MS = intent.getLongExtra("duration", 0);

            SharedPreferences prefs = getSharedPreferences("autopinger", MODE_PRIVATE);
            batterySaverMode = prefs.getBoolean("battery_saver", true);
            prefs.edit()
                    .putBoolean("was_running", true)
                    .putLong("interval_ms", BASE_INTERVAL_MS)
                    .putLong("duration_ms", DURATION_MS)
                    .apply();

            if (!isRunning) {
                isRunning = true;
                pingCount = 0;
                successCount = 0;
                consecutiveFails = 0;
                expiryTime = DURATION_MS > 0 ? System.currentTimeMillis() + DURATION_MS : 0;
                prefs.edit().putLong("expiry_time", expiryTime).apply();
                startForeground(NOTIF_ID, buildNotification());
                if (wakeLock != null) wakeLock.acquire();
                handler.removeCallbacks(pingTask);
                handler.post(pingTask);
                sendTelegram("🚀 AutoPinger Started\n🌐 " + currentUrl);
            }
        }
        return START_STICKY;
    }

    private void doPing() {
        new Thread(() -> {
            String title = "—", desc = "—", author = "—", keywords = "—", h1 = "—";
            String server = "—", contentType = "—", ip = "—";
            long responseMs = 0, contentLength = 0;
            boolean ok = false;
            int code = 0;
            String siteNameVal = "—";

            try {
                URL u = new URL(currentUrl);
                siteNameVal = u.getHost().replace("www.", "");
                boolean https = u.getProtocol().equalsIgnoreCase("https");
                try { ip = InetAddress.getByName(u.getHost()).getHostAddress(); } catch (Exception ignored) {}

                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AutoPinger/1.0");

                long t1 = System.currentTimeMillis();
                conn.connect();
                code = conn.getResponseCode();
                responseMs = System.currentTimeMillis() - t1;

                server = conn.getHeaderField("Server");
                if (server == null) server = "—";
                contentType = conn.getHeaderField("Content-Type");
                if (contentType == null) contentType = "—";
                String cl = conn.getHeaderField("Content-Length");
                try { if (cl != null) contentLength = Long.parseLong(cl); } catch (Exception ignored) {}

                StringBuilder sb = new StringBuilder();
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line; int lines = 0;
                    while ((line = br.readLine()) != null && lines < 150) { sb.append(line).append("\n"); lines++; }
                    br.close();
                } catch (Exception ignored) {}
                conn.disconnect();

                String html = sb.toString();
                title = extractTag(html, "<title[^>]*>(.*?)</title>");
                desc = extractMeta(html, "description");
                author = extractMeta(html, "author");
                keywords = extractMeta(html, "keywords");
                h1 = extractTag(html, "<h1[^>]*>(.*?)</h1>");

                if (title.length() > 100) title = title.substring(0, 100) + "...";
                if (desc.length() > 180) desc = desc.substring(0, 180) + "...";
                if (keywords.length() > 120) keywords = keywords.substring(0, 120) + "...";

                ok = code >= 200 && code < 400;
                siteProtocol = https ? "HTTPS 🔒" : "HTTP ⚠️";
            } catch (Exception ex) {
                desc = "Error: " + ex.getMessage();
            }

            final boolean success = ok;
            final String ft = title, fd = desc, fa = author, fk = keywords;
            final String fh1 = h1, fs = server, fct = contentType, fip = ip;
            final long frt = responseMs, fcl = contentLength;
            final int fc = code;
            final String fsn = siteNameVal;

            handler.post(() -> {
                pingCount++;
                if (success) { successCount++; consecutiveFails = 0; }
                else consecutiveFails++;

                siteName = fsn; siteTitle = ft; siteDescription = fd;
                siteAuthor = fa; siteKeywords = fk; siteServer = fs;
                siteIp = fip; siteContentType = fct; siteH1 = fh1;
                siteResponseMs = frt; siteCode = fc; siteContentLength = fcl;

                lastStatus = success ? "✅ Ping #" + pingCount + " OK" : "❌ Ping #" + pingCount + " Fail";

                // Smart battery saver — increase interval on repeated failures or slow response
                if (batterySaverMode) {
                    if (consecutiveFails >= 3) {
                        INTERVAL_MS = Math.min(BASE_INTERVAL_MS * 3, 30 * 60 * 1000L);
                    } else if (frt > 5000) {
                        INTERVAL_MS = Math.min(BASE_INTERVAL_MS * 2, 15 * 60 * 1000L);
                    } else {
                        INTERVAL_MS = BASE_INTERVAL_MS;
                    }
                } else {
                    INTERVAL_MS = BASE_INTERVAL_MS;
                }

                updateNotification();

                SharedPreferences prefs = getSharedPreferences("autopinger", MODE_PRIVATE);
                String tgToken = prefs.getString("tg_token", "");
                String tgChat = prefs.getString("tg_chat", "");
                if (pingCount % 5 == 0 || !success) {
                    sendTelegramWithCreds(tgToken, tgChat,
                            (success ? "✅" : "❌") + " Ping #" + pingCount +
                                    "\n🌐 " + fsn + "\n📄 " + ft +
                                    "\n📊 HTTP " + fc + " • " + frt + "ms" +
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

    private String extractTag(String html, String regex) {
        try {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            if (m.find()) return m.group(1).replaceAll("<[^>]+>", "").trim();
        } catch (Exception ignored) {}
        return "—";
    }

    private String extractMeta(String html, String name) {
        try {
            Matcher m = Pattern.compile(
                    "<meta[^>]+name=[\"']" + name + "[\"'][^>]+content=[\"']([^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) return m.group(1).trim();
            m = Pattern.compile(
                    "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+name=[\"']" + name + "[\"']",
                    Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignored) {}
        return "—";
    }

    private void stopSelfPinging(String reason) {
        isRunning = false;
        lastStatus = reason;
        handler.removeCallbacks(pingTask);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();

        SharedPreferences prefs = getSharedPreferences("autopinger", MODE_PRIVATE);
        prefs.edit().putBoolean("was_running", false).apply();

        sendTelegram("⏹️ AutoPinger Stopped\n" + reason +
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

        // Ultra-minimal silent notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(lastStatus)
                .setContentText(siteName + " • " + pct + "% • next " + nextStr)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Background Monitor", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Silent monitoring service");
            ch.setShowBadge(false);
            ch.enableVibration(false);
            ch.enableLights(false);
            ch.setSound(null, null);
            ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
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

    private void sendTelegram(String message) {
        SharedPreferences prefs = getSharedPreferences("autopinger", MODE_PRIVATE);
        sendTelegramWithCreds(prefs.getString("tg_token", ""), prefs.getString("tg_chat", ""), message);
    }

    private void sendTelegramWithCreds(final String token, final String chat, final String message) {
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
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
