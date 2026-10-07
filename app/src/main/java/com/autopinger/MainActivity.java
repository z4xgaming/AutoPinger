package com.autopinger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    EditText etUrl, etTgToken, etTgChat;
    Button btnStart, btnStop;
    ImageButton btnHistory;
    TextView tvStatus, tvInfo, tvUptime, tvExpiry, tvNextPing, tvWebsiteName, tvLiveStatus;
    TextView dSiteName, dTitle, dDescription, dAuthor, dKeywords, dServer, dIp;
    TextView dProtocol, dContentType, dResponse, dHttpCode, dContentLength, dH1;
    View liveDot;
    Spinner spDuration, spInterval;
    RainbowLoaderView loader;
    LinearLayout tgSection, tgToggle, rewardsBar;
    Switch batterySwitch;
    TextView tvAsciiProgress, tvProgressMsg;
    TextView tvCoinsSmall;

    Handler handler = new Handler(Looper.getMainLooper());
    ConnectivityManager cm;
    ConnectivityManager.NetworkCallback networkCallback;
    AlertDialog internetDialog;
    SharedPreferences prefs;

    final String[] DURATION_LABELS = {
            "1 Hour", "6 Hours", "12 Hours", "1 Day", "3 Days",
            "7 Days", "15 Days", "30 Days", "Unlimited"
    };
    final long[] DURATION_MIN = { 60, 360, 720, 1440, 4320, 10080, 21600, 43200, 0 };

    final String[] INTERVAL_LABELS = {
            "1 minute", "5 minutes", "10 minutes", "30 minutes", "1 hour"
    };
    final long[] INTERVAL_MIN = { 1, 5, 10, 30, 60 };

    Runnable uiUpdater = new Runnable() {
        @Override
        public void run() {
            updateFromService();
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
        btnHistory = findViewById(R.id.btnHistory);
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
        rewardsBar = findViewById(R.id.btnRewardsBar);
        batterySwitch = findViewById(R.id.batterySwitch);
        tvAsciiProgress = findViewById(R.id.tvAsciiProgress);
        tvProgressMsg = findViewById(R.id.tvProgressMsg);
        batterySwitch.setChecked(prefs.getBoolean("battery_saver", true));
        batterySwitch.setOnCheckedChangeListener((btn, on) -> {
            prefs.edit().putBoolean("battery_saver", on).apply();
            AutoPingerService.batterySaverMode = on;
            Toast.makeText(this, on ? "🔋 Battery saver ON" : "⚡ Performance mode", Toast.LENGTH_SHORT).show();
        });
        tvCoinsSmall = findViewById(R.id.tvCoinsSmall);
        rewardsBar.setOnClickListener(v ->
            startActivityForResult(new Intent(MainActivity.this, RewardsActivity.class), 500));

        dSiteName = findViewById(R.id.dSiteName);
        dTitle = findViewById(R.id.dTitle);
        dDescription = findViewById(R.id.dDescription);
        dAuthor = findViewById(R.id.dAuthor);
        dKeywords = findViewById(R.id.dKeywords);
        dServer = findViewById(R.id.dServer);
        dIp = findViewById(R.id.dIp);
        dProtocol = findViewById(R.id.dProtocol);
        dContentType = findViewById(R.id.dContentType);
        dResponse = findViewById(R.id.dResponse);
        dHttpCode = findViewById(R.id.dHttpCode);
        dContentLength = findViewById(R.id.dContentLength);
        dH1 = findViewById(R.id.dH1);

        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
        requestNotificationPermission();
        askBatteryOptimization();

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
        btnStop.setOnClickListener(v -> stopPinging());
        btnHistory.setOnClickListener(v -> showHistory());
        tgToggle.setOnClickListener(v ->
                tgSection.setVisibility(tgSection.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        if (AutoPingerService.isRunning) {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            etUrl.setEnabled(false);
            loader.setLoading(true);
        }

        if (!isInternetAvailable()) showInternetDialog();
        handler.post(uiUpdater);
        checkForUpdates();
    }

    private void askBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        boolean asked = prefs.getBoolean("battery_asked", false);
        if (asked) return;
        prefs.edit().putBoolean("battery_asked", true).apply();

        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                AlertDialog.Builder bldr = new AlertDialog.Builder(this);
                bldr.setTitle("🔋 Battery Optimization");
                bldr.setMessage("Background pinging smooth rakhne ke liye, AutoPinger ko battery optimization se exempt karo.\n\n" +
                        "Ye step ek hi baar karna hai — battery bachane ke liye aur service continuous chalane ke liye zaruri hai.");
                bldr.setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        try {
                            Intent i2 = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            startActivity(i2);
                        } catch (Exception ignored) {}
                    }
                });
                bldr.setNegativeButton("Baad mein", null);
                bldr.show();
            }
        } catch (Exception ignored) {}
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101); }
            catch (Exception ignored) {}
        }
    }

    private void showHistory() {
        String raw = prefs.getString("history", "");
        if (raw == null || raw.trim().isEmpty()) {
            Toast.makeText(this, "कोई history नहीं है अभी", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = raw.split("\\|\\|\\|");
        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        bldr.setTitle("🕒 URL History");
        bldr.setItems(items, (d, which) -> {
            etUrl.setText(items[which]);
            Toast.makeText(this, "URL set", Toast.LENGTH_SHORT).show();
        });
        bldr.setNegativeButton("Clear All", (d, w) -> {
            prefs.edit().remove("history").apply();
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
        });
        bldr.show();
    }

    private void addToHistory(String url) {
        String raw = prefs.getString("history", "");
        List<String> list = new ArrayList<>();
        if (!raw.trim().isEmpty()) list.addAll(Arrays.asList(raw.split("\\|\\|\\|")));
        list.remove(url);
        list.add(0, url);
        while (list.size() > 10) list.remove(list.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("|||");
            sb.append(list.get(i));
        }
        prefs.edit().putString("history", sb.toString()).apply();
    }

    private void registerNetworkCallback() {
        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                handler.post(() -> hideInternetDialog());
            }
            @Override public void onLost(Network network) {
                handler.post(() -> { if (!isInternetAvailable()) showInternetDialog(); });
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

        long durationMin = DURATION_MIN[durIdx];
        long intervalMs = INTERVAL_MIN[intIdx] * 60 * 1000L;
        long durationMs = durationMin * 60 * 1000L;

        prefs.edit()
                .putString("url", url)
                .putString("tg_token", etTgToken.getText().toString().trim())
                .putString("tg_chat", etTgChat.getText().toString().trim())
                .putInt("duration_idx", durIdx)
                .putInt("interval_idx", intIdx)
                .putLong("interval_ms", intervalMs)
                .putLong("duration_ms", durationMs)
                .putBoolean("was_running", true)
                .apply();

        addToHistory(url);

        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(etUrl.getWindowToken(), 0);
        } catch (Exception ignored) {}

        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        etUrl.setEnabled(false);
        spDuration.setEnabled(false);
        spInterval.setEnabled(false);
        loader.setLoading(true);
        tvStatus.setText("🚀 Starting service...");

        // 🖤 ASCII BLACK PROGRESS ANIMATION
        final String fUrl = url;
        final long fInterval = intervalMs;
        final long fDuration = durationMs;
        animateAsciiProgress(new Runnable() {
            @Override public void run() {
                Intent svc = new Intent(MainActivity.this, AutoPingerService.class);
                svc.putExtra("url", fUrl);
                svc.putExtra("interval", fInterval);
                svc.putExtra("duration", fDuration);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            }
        });
    }

    private void stopPinging() {
        Intent svc = new Intent(this, AutoPingerService.class);
        stopService(svc);
        AutoPingerService.isRunning = false;
        prefs.edit().putBoolean("was_running", false).apply();
        loader.setLoading(false);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        etUrl.setEnabled(true);
        spDuration.setEnabled(true);
        spInterval.setEnabled(true);
        tvStatus.setText("⏹️ Stopped");
        setLiveStatus(false);
        tvNextPing.setText("Next ping: —");
        if (tvAsciiProgress != null) tvAsciiProgress.setVisibility(View.GONE);
        if (tvProgressMsg != null) tvProgressMsg.setVisibility(View.GONE);
    }

    private void updateCoinsUI() {
        if (tvCoinsSmall != null) tvCoinsSmall.setText("" + WalletManager.getCoins(this));
    }

    private void updateFromService() {
        updateCoinsUI();
        if (AutoPingerService.isRunning) {
            int p = AutoPingerService.pingCount;
            int s = AutoPingerService.successCount;
            int pct = p > 0 ? (s * 100 / p) : 0;

            tvStatus.setText(AutoPingerService.lastStatus);
            tvWebsiteName.setText("Website: " + AutoPingerService.siteName);
            tvUptime.setText("Uptime: " + pct + "%  |  Pings: " + p + "  |  Success: " + s);
            setLiveStatus(p > 0 && s == p);

            long exp = AutoPingerService.expiryTime;
            if (exp > 0) {
                long left = Math.max(0, exp - System.currentTimeMillis());
                long d = left / 86400000L;
                long h = (left % 86400000L) / 3600000L;
                long m = (left % 3600000L) / 60000L;
                long sec = (left % 60000L) / 1000L;
                tvExpiry.setText("Expires in: " + d + "d " + h + "h " + m + "m " + sec + "s");
            } else {
                tvExpiry.setText("Expires in: ♾️ Unlimited");
            }

            long np = AutoPingerService.nextPingTime;
            if (np > 0) {
                long left = Math.max(0, np - System.currentTimeMillis());
                tvNextPing.setText("Next ping in: " + (left / 60000L) + "m " + ((left % 60000L) / 1000L) + "s");
            }

            updateDetails();

            loader.setLoading(true);
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            etUrl.setEnabled(false);
            spDuration.setEnabled(false);
            spInterval.setEnabled(false);
        }
    }

    private void updateDetails() {
        dSiteName.setText("🌐 Website: " + AutoPingerService.siteName);
        dTitle.setText("📄 Title: " + AutoPingerService.siteTitle);
        dDescription.setText("📝 Description: " + AutoPingerService.siteDescription);
        dAuthor.setText("👤 Author: " + AutoPingerService.siteAuthor);
        dKeywords.setText("🔑 Keywords: " + AutoPingerService.siteKeywords);
        dServer.setText("🖥️ Server: " + AutoPingerService.siteServer);
        dIp.setText("📍 IP: " + AutoPingerService.siteIp);
        dProtocol.setText("🔒 Protocol: " + AutoPingerService.siteProtocol);
        dContentType.setText("📦 Content-Type: " + AutoPingerService.siteContentType);
        dResponse.setText("⚡ Response: " + AutoPingerService.siteResponseMs + " ms");
        dHttpCode.setText("📊 HTTP: " + AutoPingerService.siteCode);
        dContentLength.setText("📏 Size: " + (AutoPingerService.siteContentLength / 1024) + " KB");
        dH1.setText("🅷 H1: " + AutoPingerService.siteH1);
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

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 500 && res == RESULT_OK && data != null) {
            int mins = data.getIntExtra("redeem_minutes", 0);
            if (mins > 0) {
                prefs.edit().putLong("bonus_minutes",
                        prefs.getLong("bonus_minutes", 0) + mins).apply();
                Toast.makeText(this, "🎁 " + mins + " min added to your bonus!",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkForUpdates() {
        UpdateChecker.check(this, (avail, ver, url, log) -> {
            if (avail && url != null) {
                new AlertDialog.Builder(this)
                        .setTitle("🚀 Update Available")
                        .setMessage("v" + ver + " ready\n\n" +
                                (log != null && log.length() > 250 ? log.substring(0, 250) + "..." : log))
                        .setPositiveButton("Download", (d, w) -> {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                            catch (Exception e) { Toast.makeText(this, "Browser missing", Toast.LENGTH_SHORT).show(); }
                        })
                        .setNegativeButton("Later", null).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(uiUpdater);
        hideInternetDialog();
        try { if (networkCallback != null) cm.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void animateAsciiProgress(final Runnable onComplete) {
        if (tvAsciiProgress == null) { if (onComplete != null) onComplete.run(); return; }
        tvAsciiProgress.setVisibility(View.VISIBLE);
        if (tvProgressMsg != null) tvProgressMsg.setVisibility(View.VISIBLE);
        tvAsciiProgress.setBackgroundResource(R.drawable.ascii_progress_bg);

        final int TOTAL_BLOCKS = 10;
        final long STEP_MS = 25L;

        final int[] step = {0};
        final Handler h = new Handler(Looper.getMainLooper());

        Runnable tick = new Runnable() {
            @Override public void run() {
                int pct = step[0];
                int filled = (pct * TOTAL_BLOCKS) / 100;
                StringBuilder bar = new StringBuilder("[");
                for (int i = 0; i < TOTAL_BLOCKS; i++) {
                    bar.append(i < filled ? "\u2593" : "\u2591");
                }
                bar.append("]  ").append(pct).append("%");
                tvAsciiProgress.setText(bar.toString());

                String msg;
                if (pct < 20) msg = "Initializing engine...";
                else if (pct < 40) msg = "Connecting to websites...";
                else if (pct < 60) msg = "Pinging servers...";
                else if (pct < 80) msg = "Analyzing responses...";
                else if (pct < 99) msg = "Going live...";
                else msg = "ALL SYSTEMS LIVE!";
                if (tvProgressMsg != null) tvProgressMsg.setText(msg);

                if (step[0] >= 100) {
                    h.postDelayed(new Runnable() {
                        @Override public void run() {
                            tvAsciiProgress.setVisibility(View.GONE);
                            if (tvProgressMsg != null) tvProgressMsg.setVisibility(View.GONE);
                            if (onComplete != null) onComplete.run();
                        }
                    }, 700);
                    return;
                }
                step[0] += 2;
                h.postDelayed(this, STEP_MS);
            }
        };
        h.post(tick);
    }

}
