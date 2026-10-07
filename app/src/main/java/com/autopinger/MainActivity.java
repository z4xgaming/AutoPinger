package com.autopinger;

import android.app.AlertDialog;
import android.content.Context;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    EditText etUrl;
    Button btnStart, btnStop;
    TextView tvStatus, tvInfo;
    RainbowLoaderView loader;

    Handler handler = new Handler(Looper.getMainLooper());
    ConnectivityManager cm;
    ConnectivityManager.NetworkCallback networkCallback;
    AlertDialog internetDialog;
    boolean running = false;
    int pingCount = 0, successCount = 0;
    String currentUrl = "";
    long INTERVAL_MS = 5 * 60 * 1000L;

    Runnable pingTask = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        etUrl = findViewById(R.id.etUrl);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        loader = findViewById(R.id.loader);

        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();

        btnStart.setOnClickListener(v -> startPinging());
        btnStop.setOnClickListener(v -> stopPinging());

        if (!isInternetAvailable()) showInternetDialog();
    }

    private void registerNetworkCallback() {
        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                handler.post(() -> {
                    hideInternetDialog();
                    tvStatus.setText("✅ Internet चालू हो गया");
                    if (running) {
                        handler.removeCallbacks(pingTask);
                        handler.post(pingTask);
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                handler.post(() -> {
                    if (running || !isInternetAvailable()) showInternetDialog();
                });
            }
        };

        try {
            cm.registerNetworkCallback(req, networkCallback);
        } catch (Exception ignored) {}
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.view.View v = LayoutInflater.from(this).inflate(R.layout.dialog_no_internet, null);
        builder.setView(v);
        builder.setCancelable(false);
        internetDialog = builder.create();

        if (internetDialog.getWindow() != null) {
            internetDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
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
        if (url.isEmpty()) {
            tvStatus.setText("⚠️ URL dalo bhai!");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        currentUrl = url;
        running = true;
        pingCount = 0;
        successCount = 0;

        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(etUrl.getWindowToken(), 0);
        } catch (Exception ignored) {}

        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        etUrl.setEnabled(false);

        if (!isInternetAvailable()) {
            showInternetDialog();
            tvInfo.setText("Internet चालू करने का इंतज़ार...\nचालू होते ही automatic ping शुरू होगी.");
            return;
        }

        tvStatus.setText("🚀 Starting...");
        handler.post(pingTask);
    }

    private void stopPinging() {
        running = false;
        handler.removeCallbacks(pingTask);
        hideInternetDialog();
        loader.setLoading(false);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        etUrl.setEnabled(true);
        tvStatus.setText("⏹️ Stopped");
        tvInfo.setText("Pinger बंद कर दिया.\n\nTotal Pings: " + pingCount +
                "\nSuccess: " + successCount + "\nFailed: " + (pingCount - successCount));
    }

    private void doPing() {
        new Thread(() -> {
            String resultMsg;
            boolean ok = false;
            try {
                URL u = new URL(currentUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AutoPinger/1.0");
                conn.connect();

                int code = conn.getResponseCode();
                String title = "";
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    int lines = 0;
                    while ((line = br.readLine()) != null && lines < 60) {
                        sb.append(line);
                        lines++;
                    }
                    br.close();
                    String html = sb.toString();
                    int s = html.indexOf("<title>");
                    int e = html.indexOf("</title>");
                    if (s >= 0 && e > s) title = html.substring(s + 7, e).trim();
                } catch (Exception ignored) {}
                conn.disconnect();

                ok = code >= 200 && code < 400;
                resultMsg = "Status: " + code + (title.isEmpty() ? "" : "\nTitle: " + title);
            } catch (Exception ex) {
                resultMsg = "Error: " + ex.getMessage();
            }

            final boolean success = ok;
            final String msg = resultMsg;
            handler.post(() -> {
                pingCount++;
                if (success) successCount++;
                tvStatus.setText(success ? "✅ Ping #" + pingCount + " Success" : "❌ Ping #" + pingCount + " Failed");
                tvInfo.setText(msg + "\n\nTotal: " + pingCount + " | Success: " + successCount +
                        "\nNext ping 5 min baad...\nURL: " + currentUrl);
                loader.setLoading(true);

                if (!running) return;
                if (!isInternetAvailable()) showInternetDialog();
                else handler.postDelayed(pingTask, INTERVAL_MS);
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        running = false;
        handler.removeCallbacks(pingTask);
        hideInternetDialog();
        try {
            if (networkCallback != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
