package com.autopinger;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class RewardsActivity extends AppCompatActivity {

    TextView tvCoins, tvTotalEarned, tvStreak, tvDailyTimer, tvScratchTimer;
    Button btnDailySpin, btnScratch, btnPromo, btnBack;
    Button btnR1h, btnR6h, btnR1d, btnR7d, btnR30d;
    EditText etPromo;
    Handler h = new Handler(Looper.getMainLooper());
    Random rnd = new Random();

    Runnable tick = new Runnable() {
        @Override public void run() { updateUI(); h.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_rewards);

        tvCoins = findViewById(R.id.tvCoins);
        tvTotalEarned = findViewById(R.id.tvTotalEarned);
        tvStreak = findViewById(R.id.tvStreak);
        tvDailyTimer = findViewById(R.id.tvDailyTimer);
        tvScratchTimer = findViewById(R.id.tvScratchTimer);
        btnDailySpin = findViewById(R.id.btnDailySpin);
        btnScratch = findViewById(R.id.btnScratch);
        btnPromo = findViewById(R.id.btnPromo);
        btnBack = findViewById(R.id.btnBack);
        etPromo = findViewById(R.id.etPromo);
        btnR1h = findViewById(R.id.btnR1h);
        btnR6h = findViewById(R.id.btnR6h);
        btnR1d = findViewById(R.id.btnR1d);
        btnR7d = findViewById(R.id.btnR7d);
        btnR30d = findViewById(R.id.btnR30d);

        btnDailySpin.setOnClickListener(v -> doSpin());
        btnScratch.setOnClickListener(v -> doScratch());
        btnPromo.setOnClickListener(v -> doPromo());
        btnBack.setOnClickListener(v -> finish());

        btnR1h.setOnClickListener(v -> redeem(100, 60, "1 Hour"));
        btnR6h.setOnClickListener(v -> redeem(500, 360, "6 Hours"));
        btnR1d.setOnClickListener(v -> redeem(1500, 1440, "1 Day"));
        btnR7d.setOnClickListener(v -> redeem(8000, 10080, "7 Days"));
        btnR30d.setOnClickListener(v -> redeem(25000, 43200, "30 Days"));

        updateUI();
        h.post(tick);
    }

    private void updateUI() {
        tvCoins.setText(String.valueOf(WalletManager.getCoins(this)));
        tvTotalEarned.setText("Total earned: " + WalletManager.getTotalEarned(this));
        int st = WalletManager.getStreak(this);
        tvStreak.setText("🔥 " + st + " day" + (st == 1 ? "" : "s") + " streak");

        if (WalletManager.canClaimDaily(this)) {
            btnDailySpin.setEnabled(true); btnDailySpin.setAlpha(1f);
            tvDailyTimer.setText("✅ Spin now!");
        } else {
            btnDailySpin.setEnabled(false); btnDailySpin.setAlpha(0.55f);
            long left = WalletManager.dailyNextTime(this) - System.currentTimeMillis();
            tvDailyTimer.setText("⏳ Next spin in " + fmt(left));
        }

        if (WalletManager.canClaimScratch(this)) {
            btnScratch.setEnabled(true); btnScratch.setAlpha(1f);
            tvScratchTimer.setText("✅ Scratch now!");
        } else {
            btnScratch.setEnabled(false); btnScratch.setAlpha(0.55f);
            long left = WalletManager.scratchNextTime(this) - System.currentTimeMillis();
            tvScratchTimer.setText("⏳ Next scratch in " + fmt(left));
        }
    }

    private String fmt(long ms) {
        if (ms < 0) ms = 0;
        long h = ms / 3600000L, m = (ms % 3600000L) / 60000L, s = (ms % 60000L) / 1000L;
        return h + "h " + m + "m " + s + "s";
    }

    private void doSpin() {
        if (!WalletManager.canClaimDaily(this)) return;
        // BALANCED: 5-25 coins
        int reward = 5 + rnd.nextInt(21);
        int nextStreak = WalletManager.getStreak(this) + 1;
        String bonus = null;
        if (nextStreak == 7) { reward += 100; bonus = "🔥 7-Day Streak! +100 bonus"; }

        WalletManager.setDailyClaimed(this);
        WalletManager.addCoins(this, reward);
        showWin(reward, bonus);
        updateUI();
    }

    private void doScratch() {
        if (!WalletManager.canClaimScratch(this)) return;
        // BALANCED: 3-15 coins
        int reward = 3 + rnd.nextInt(13);
        WalletManager.setScratchClaimed(this);
        WalletManager.addCoins(this, reward);
        showWin(reward, null);
        updateUI();
    }

    private void showWin(int amount, String bonus) {
        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_spin_result, null);
        ((TextView) v.findViewById(R.id.tvWinAmount)).setText("+" + amount);
        if (bonus != null) {
            TextView t = new TextView(this);
            t.setText(bonus);
            t.setTextColor(0xFFFF6B00);
            t.setTextSize(12);
            t.setPadding(0, 10, 0, 0);
            LinearLayout parent = (LinearLayout) v;
            parent.addView(t, parent.getChildCount() - 1);
        }
        final AlertDialog d = bldr.setView(v).create();
        if (d.getWindow() != null)
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        v.findViewById(R.id.btnOk).setOnClickListener(x -> d.dismiss());
        d.show();
    }

    private void doPromo() {
        String code = etPromo.getText().toString().trim();
        PromoManager.Result r = PromoManager.redeem(this, code);
        if (r.valid) {
            etPromo.setText("");
            new AlertDialog.Builder(this)
                    .setTitle("🎁 Code Applied!")
                    .setMessage("+" + r.coins + " 💎\n\n" + r.label)
                    .setPositiveButton("Nice!", null).show();
            updateUI();
        } else {
            Toast.makeText(this, r.error, Toast.LENGTH_SHORT).show();
        }
    }

    private void redeem(final int cost, final int minutes, final String label) {
        if (WalletManager.getCoins(this) < cost) {
            Toast.makeText(this, "❌ Need " + cost + " 💎", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage("Spend " + cost + " 💎 for " + label + " monitoring?")
                .setPositiveButton("Redeem", (d, w) -> {
                    if (WalletManager.spendCoins(this, cost)) {
                        Intent i = new Intent();
                        i.putExtra("redeem_minutes", minutes);
                        setResult(RESULT_OK, i);
                        Toast.makeText(this, "✅ " + label + " unlocked!", Toast.LENGTH_LONG).show();
                        updateUI();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    @Override protected void onDestroy() { h.removeCallbacks(tick); super.onDestroy(); }
}
