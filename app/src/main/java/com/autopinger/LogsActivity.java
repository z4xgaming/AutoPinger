package com.autopinger;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class LogsActivity extends AppCompatActivity {

    TextView tvLogs;
    ScrollView scrollLogs;
    Handler h = new Handler(Looper.getMainLooper());

    Runnable updater = new Runnable() {
        @Override public void run() {
            StringBuilder sb = new StringBuilder();
            for (String line : AutoPingerService.logs) sb.append(line).append("\n");
            if (sb.length() == 0) sb.append("Waiting for activity...");
            tvLogs.setText(sb.toString());
            scrollLogs.post(() -> scrollLogs.fullScroll(ScrollView.FOCUS_UP));
            h.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_logs);
        tvLogs = findViewById(R.id.tvLogs);
        scrollLogs = findViewById(R.id.scrollLogs);
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            AutoPingerService.logs.clear();
            tvLogs.setText("Cleared.");
        });
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        h.post(updater);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(updater);
        super.onDestroy();
    }
}
