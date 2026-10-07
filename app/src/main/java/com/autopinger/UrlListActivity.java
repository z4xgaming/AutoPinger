package com.autopinger;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UrlListActivity extends AppCompatActivity {

    EditText etNewUrl;
    Button btnAdd, btnStartAll, btnBack, btnClearHistory;
    TextView tabActive, tabHistory, tvActiveCount, tvEmpty;
    ListView lvSites;
    List<UrlManager.Site> sites;
    SiteAdapter adapter;
    boolean showingActive = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_url_list);

        etNewUrl = findViewById(R.id.etNewUrl);
        btnAdd = findViewById(R.id.btnAdd);
        btnStartAll = findViewById(R.id.btnStartAll);
        btnBack = findViewById(R.id.btnBack);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        tabActive = findViewById(R.id.tabActive);
        tabHistory = findViewById(R.id.tabHistory);
        tvActiveCount = findViewById(R.id.tvActiveCount);
        lvSites = findViewById(R.id.lvSites);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new SiteAdapter();
        lvSites.setAdapter(adapter);
        loadSites();
        updateTabs();

        btnAdd.setOnClickListener(v -> {
            String url = etNewUrl.getText().toString().trim();
            if (url.isEmpty()) { Toast.makeText(this, "URL dalo", Toast.LENGTH_SHORT).show(); return; }
            UrlManager.add(this, url);
            etNewUrl.setText("");
            loadSites();
            Toast.makeText(this, "✅ Added", Toast.LENGTH_SHORT).show();
        });

        tabActive.setOnClickListener(v -> { showingActive = true; loadSites(); updateTabs(); });
        tabHistory.setOnClickListener(v -> { showingActive = false; loadSites(); updateTabs(); });

        btnStartAll.setOnClickListener(v -> {
            List<UrlManager.Site> active = UrlManager.loadActive(this);
            if (active.isEmpty()) {
                Toast.makeText(this, "Pehle URLs add karein", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("multi_start", true);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });

        btnClearHistory.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear History?")
                    .setMessage("Saari history delete ho jayegi.")
                    .setPositiveButton("Clear", (d, w) -> {
                        UrlManager.clearHistory(this);
                        loadSites();
                        updateTabs();
                        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null).show();
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadSites() {
        sites = showingActive ? UrlManager.loadActive(this) : UrlManager.loadHistory(this);
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(sites.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText(showingActive ? "Koi active website nahi.\nUpar se URL add karein." : "Koi history nahi hai.");
        lvSites.setVisibility(sites.isEmpty() ? View.GONE : View.VISIBLE);

        int active = UrlManager.loadActive(this).size();
        tvActiveCount.setText(active + " active");
    }

    private void updateTabs() {
        if (showingActive) {
            tabActive.setBackgroundResource(R.drawable.coin_gold);
            tabActive.setTextColor(Color.parseColor("#000000"));
            tabHistory.setBackgroundResource(R.drawable.input_bg);
            tabHistory.setTextColor(Color.parseColor("#FFFFFF"));
            btnStartAll.setVisibility(View.VISIBLE);
            btnClearHistory.setVisibility(View.GONE);
        } else {
            tabActive.setBackgroundResource(R.drawable.input_bg);
            tabActive.setTextColor(Color.parseColor("#FFFFFF"));
            tabHistory.setBackgroundResource(R.drawable.coin_gold);
            tabHistory.setTextColor(Color.parseColor("#000000"));
            btnStartAll.setVisibility(View.GONE);
            btnClearHistory.setVisibility(View.VISIBLE);
        }
    }

    class SiteAdapter extends BaseAdapter {
        @Override public int getCount() { return sites.size(); }
        @Override public Object getItem(int i) { return sites.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View cv, ViewGroup p) {
            if (cv == null)
                cv = LayoutInflater.from(UrlListActivity.this).inflate(R.layout.item_site, p, false);
            UrlManager.Site s = sites.get(i);

            View dot = cv.findViewById(R.id.dot);
            TextView name = cv.findViewById(R.id.tvSiteName);
            TextView badge = cv.findViewById(R.id.tvStatusBadge);
            TextView http = cv.findViewById(R.id.tvHttp);
            TextView uptime = cv.findViewById(R.id.tvUptime);
            TextView speed = cv.findViewById(R.id.tvSpeed);
            TextView pings = cv.findViewById(R.id.tvPings);
            Button move = cv.findViewById(R.id.btnMove);
            Button del = cv.findViewById(R.id.btnDelete);

            name.setText(s.name);

            if (s.pings == 0) {
                badge.setText("NEW");
                badge.setTextColor(Color.parseColor("#90FFFFFF"));
                dot.setBackgroundResource(R.drawable.dot_gray);
                http.setText("—");
                uptime.setText("0%");
                speed.setText("— ms");
            } else {
                if (s.live) {
                    badge.setText("● LIVE");
                    badge.setTextColor(Color.parseColor("#00E676"));
                    dot.setBackgroundResource(R.drawable.dot_green);
                } else {
                    badge.setText("● OFFLINE");
                    badge.setTextColor(Color.parseColor("#FF1744"));
                    dot.setBackgroundResource(R.drawable.dot_red);
                }
                http.setText(String.valueOf(s.code));
                uptime.setText(s.getUptimePercent() + "%");
                speed.setText(s.responseMs + "ms");
            }
            pings.setText(String.valueOf(s.pings));

            // Move button — history me bhejna
            if (showingActive) {
                move.setText("📜 Move to History");
                move.setOnClickListener(v -> {
                    UrlManager.moveToHistory(UrlListActivity.this, s.url);
                    loadSites();
                    updateTabs();
                    Toast.makeText(UrlListActivity.this,
                            "📜 Moved to History", Toast.LENGTH_SHORT).show();
                });
            } else {
                move.setText("⬆️ Restore");
                move.setOnClickListener(v -> {
                    UrlManager.moveToActive(UrlListActivity.this, s.url);
                    loadSites();
                    updateTabs();
                    Toast.makeText(UrlListActivity.this,
                            "✅ Restored to Active", Toast.LENGTH_SHORT).show();
                });
            }

            // Delete
            del.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(UrlListActivity.this)
                        .setTitle("Delete " + s.name + "?")
                        .setPositiveButton("Delete", (d, w) -> {
                            UrlManager.remove(UrlListActivity.this, s.url);
                            loadSites();
                            updateTabs();
                            Toast.makeText(UrlListActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null).show();
            });

            return cv;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSites();
        updateTabs();
    }
}
