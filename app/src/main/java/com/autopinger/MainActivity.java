package com.autopinger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    EditText etUrl, etTgToken, etTgChat;
    Button btnStart, btnStop;
    TextView tvStatus, tvInfo, tvUptime, tvExpiry, tvNextPing, tvWebsiteName, tvLiveStatus;
    View liveDot;
    Spinner spDuration, spInterval;
    RainbowLoaderView loader;
    LinearLayout tgSection, tgToggle;

    Handler handler = new Handler(Looper.getMainLooper());
    ConnectivityManager cm;
    ConnectivityManager.NetworkCallback networkCallback;
    AlertDialog internetDialog;
    SharedPreferences prefs;

    boolean running = false;
    int pingCount = 0, successCount = 0;
    String currentUrl = "";
    String currentTitle = "—";
    String currentSiteName = "—";
    long startTime = 0;
    long expiryTime = 0;
    long nextPingTime = 0;
    long INTERVAL_MS = 5 * 60 * 1000L;
    long DURATION_MINUTES = 0;

    final String[] DURATION_LABELS = {
            "1 Hour", "6 Hours", "12 Hours", "1 Day", "3 Days",
            "7 Days", "15 Days", "30 Days", "Unlimited"
    };
    final long[] DURATION_MIN = { 60, 360, 720, 1440, 4320, 10080, 21600, 43200, 0 };

    final String[] INTERVAL_LABELS = {
            "1 minute", "5 minutes", "10 minutes", "30 minutes", "1 hour"
    };
    final long[] INTERVAL_MIN = { 1, 5, 10, 30, 60 };

    Runnable pingTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (expiryTime > 0 && System.currentTimeMillis() >= expiryTime) {
                stopPinging("⏰ Duration khatam! App ruk gayi.");
                return;
            }
            if (!isInternetAvailable()) {
                showInternetDialog();
                return;
            }
            hideInternetDialog();
            loader.setLoading(true);
            tvStatus.setText("🌐 Request bhej raha hai...");
            doPing();
        }
    };

    Runnable countdownTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            updateCountdowns();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("autopinger", MODE_PRIVATE);

        etUrl = findViewById(R.id.etUrl);
        etTgToken = findViewById(R.id.etTgToken);
        etTgChat = findViewById(R.id.etTgChat);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        tvUptime = findViewById(R.id.tvUptime);
        tvExpiry = findViewById(R.id.tvExpiry);
        tvNextPing = findViewById(R.id.tvNextPing);
        tvWebsiteName = findViewById(R.id.tvWebsiteName);
        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        liveDot = findViewById(R.id.liveDot);
        spDuration = findViewById(R.id.spDuration);
        spInterval = findViewById(R.id.spInterval);
        loader = findViewById(R.id.loader);
        tgSection = findViewById(R.id.tgSection);
        tgToggle = findViewById(R.id.tgToggle);

        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();

        etUrl.setText(prefs.getString("url", ""));
        etTgToken.setText(prefs.getString("tg_token", ""));
        etTgChat.setText(prefs.getString("tg_chat", ""));

        ArrayAdapter<String> dAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, DURATION_LABELS);
        dAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDuration.setAdapter(dAd);
        spDuration.setSelection(prefs.getInt("duration_idx", 4));

        ArrayAdapter<String> iAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, INTERVAL_LABELS);
        iAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spInterval.setAdapter(iAd);
        spInterval.setSelection(prefs.getInt("interval_idx", 1));

        btnStart.setOnClickListener(v -> startPinging());
        btnStop.setOnClickListener(v -> stopPinging("⏹️ Manually roka gaya"));

        tgToggle.setOnClickListener(v ->
                tgSection.setVisibility(tgSection.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        if (!isInternetAvailable()) showInternetDialog();
    }

    private void registerNetworkCallback() {
        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                handler.post(() -> {
                    hideInternetDialog();
                    if (running) {
                        handler.removeCallbacks(pingTask);
                        handler.post(pingTask);
                    }
                });
            }
            @Override public void onLost(Network network) {
                handler.post(() -> { if (running || !isInternetAvailable()) showInternetDialog(); });
            }
        };
        try { cm.registerNetworkCallback(req, networkCallback); } catch (Exception ignored) {}
    }

    private boolean isInternetAvailable() {
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(n);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showInternetDialog() {
        if (internetDialog != null && internetDialog.isShowing()) return;
        if (isFinishing()) return;
        loader.setLoading(false);
        tvStatus.setText("📴 Internet बंद है");
        setLiveStatus(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_no_internet, null);
        builder.setView(v);
        builder.setCancelable(false);
        internetDialog = builder.create();
        if (internetDialog.getWindow() != null)
            internetDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        internetDialog.show();
    }

    private void hideInternetDialog() {
        if (internetDialog != null && internetDialog.isShowing()) {
            internetDialog.dismiss();
            internetDialog = null;
        }
    }

    private void startPinging() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { tvStatus.setText("⚠️ URL dalo bhai!"); return; }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;

        int durIdx = spDuration.getSelectedItemPosition();
        int intIdx = spInterval.getSelectedItemPosition();

        DURATION_MINUTES = DURATION_MIN[durIdx];
        INTERVAL_MS = INTERVAL_MIN[intIdx] * 60 * 1000L;

        currentUrl = url;
        running = true;
        pingCount = 0;
        successCount = 0;
        startTime = System.currentTimeMillis();
        expiryTime = DURATION_MINUTES > 0 ? startTime + DURATION_MINUTES * 60 * 1000L : 0;

        prefs.edit()
                .putString("url", url)
                .putString("tg_token", etTgToken.getText().toString().trim())
                .putString("tg_chat", etTgChat.getText().toString().trim())
                .putInt("duration_idx", durIdx)
                .putInt("interval_idx", intIdx)
                .apply();

        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(etUrl.getWindowToken(), 0);
        } catch (Exception ignored) {}

        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        etUrl.setEnabled(false);
        spDuration.setEnabled(false);
        spInterval.setEnabled(false);

        if (!isInternetAvailable()) {
            showInternetDialog();
            tvInfo.setText("Internet चालू करने का इंतज़ार...\nचालू होते ही automatic ping शुरू होगी.");
            return;
        }

        tvStatus.setText("🚀 Starting...");
        sendTelegram("🚀 AutoPinger Started\n🌐 " + currentUrl + "\n⏱️ Interval: " + INTERVAL_LABELS[intIdx] + "\n📅 Duration: " + DURATION_LABELS[durIdx]);

        handler.removeCallbacks(pingTask);
        handler.removeCallbacks(countdownTask);
        handler.post(pingTask);
        handler.post(countdownTask);
    }

    private void stopPinging(String reason) {
        running = false;
        handler.removeCallbacks(pingTask);
        handler.removeCallbacks(countdownTask);
        hideInternetDialog();
        loader.setLoading(false);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        etUrl.setEnabled(true);
        spDuration.setEnabled(true);
        spInterval.setEnabled(true);
        tvStatus.setText(reason);
        setLiveStatus(false);
        tvNextPing.setText("Next ping: —");
        updateStats();

        sendTelegram("⏹️ AutoPinger Stopped\n" + reason + "\n🌐 " + currentUrl +
                "\n📊 Total: " + pingCount + " | ✅ " + successCount + " | ❌ " + (pingCount - successCount));
    }

    private void doPing() {
        new Thread(() -> {
            String title = "—", siteName = "—", resultMsg;
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
                if (title.length() > 80) title = title.substring(0, 80) + "...";

                ok = code >= 200 && code < 400;
                resultMsg = "HTTP " + code + "\n📄 " + title;
            } catch (Exception ex) {
                resultMsg = "❌ " + ex.getMessage();
            }

            final boolean success = ok;
            final String msg = resultMsg;
            final String t = title;
            final String sn = siteName;
            final int c = code;

            handler.post(() -> {
                pingCount++;
                if (success) successCount++;
                currentTitle = t;
                currentSiteName = sn;
                tvStatus.setText(success ? "✅ Ping #" + pingCount + " Success" : "❌ Ping #" + pingCount + " Failed");
                tvInfo.setText(msg + "\n\n🌐 " + currentUrl);
                setLiveStatus(success);
                updateStats();
                loader.setLoading(true);

                if (pingCount % 5 == 0 || !success) {
                    sendTelegram((success ? "✅" : "❌") + " Ping #" + pingCount +
                            "\n🌐 " + sn + "\n📊 HTTP " + c + "\n📄 " + t);
                }

                if (!running) return;
                if (expiryTime > 0 && System.currentTimeMillis() >= expiryTime) {
                    stopPinging("⏰ Duration khatam! App ruk gayi.");
                    return;
                }
                if (!isInternetAvailable()) { showInternetDialog(); return; }
                nextPingTime = System.currentTimeMillis() + INTERVAL_MS;
                handler.postDelayed(pingTask, INTERVAL_MS);
            });
        }).start();
    }

    private void updateStats() {
        int pct = pingCount > 0 ? (int) ((successCount * 100.0) / pingCount) : 0;
        tvUptime.setText("Uptime: " + pct + "%  |  Pings: " + pingCount + "  |  Success: " + successCount);
        tvWebsiteName.setText("Website: " + currentSiteName + "  —  " + currentTitle);
    }

    private void updateCountdowns() {
        long now = System.currentTimeMillis();
        if (expiryTime > 0) {
            long left = expiryTime - now;
            if (left < 0) left = 0;
            long d = left / 86400000L;
            long h = (left % 86400000L) / 3600000L;
            long m = (left % 3600000L) / 60000L;
            long s = (left % 60000L) / 1000L;
            tvExpiry.setText("Expires in: " + d + "d " + h + "h " + m + "m " + s + "s");
        } else {
            tvExpiry.setText("Expires in: ♾️ Unlimited");
        }
        if (nextPingTime > 0) {
            long left = nextPingTime - now;
            if (left < 0) left = 0;
            long m = left / 60000L;
            long s = (left % 60000L) / 1000L;
            tvNextPing.setText("Next ping in: " + m + "m " + s + "s");
        }
    }

    private void setLiveStatus(boolean live) {
        if (live) {
            liveDot.setBackgroundResource(R.drawable.dot_green);
            tvLiveStatus.setText("● LIVE");
            tvLiveStatus.setTextColor(0xFF00E676);
        } else {
            liveDot.setBackgroundResource(R.drawable.dot_red);
            tvLiveStatus.setText("● OFFLINE");
            tvLiveStatus.setTextColor(0xFFFF1744);
        }
    }

    private void sendTelegram(final String message) {
        final String token = etTgToken.getText().toString().trim();
        final String chat = etTgChat.getText().toString().trim();
        if (token.isEmpty() || chat.isEmpty()) return;
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
    protected void onDestroy() {
        running = false;
        handler.removeCallbacks(pingTask);
        handler.removeCallbacks(countdownTask);
        hideInternetDialog();
        try { if (networkCallback != null) cm.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
