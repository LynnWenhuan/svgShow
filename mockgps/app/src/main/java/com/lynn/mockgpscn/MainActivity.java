package com.lynn.mockgpscn;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText latInput;
    private EditText lonInput;
    private TextView status;
    private TextView diag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("Mock GPS CN");
        title.setTextSize(24f);
        root.addView(title);

        status = new TextView(this);
        status.setText("状态：未启动");
        root.addView(status);

        diag = new TextView(this);
        diag.setText("诊断：暂无");
        diag.setTextSize(12f);
        root.addView(diag);

        WebView map = new WebView(this);
        map.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        WebSettings ws = map.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        map.addJavascriptInterface(new MapBridge(), "AndroidBridge");
        map.loadUrl("file:///android_asset/map.html");
        root.addView(map);

        latInput = new EditText(this);
        latInput.setHint("纬度，例如 22.543096");
        latInput.setText("22.543096");
        root.addView(latInput);

        lonInput = new EditText(this);
        lonInput.setHint("经度，例如 114.057865");
        lonInput.setText("114.057865");
        root.addView(lonInput);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button developer = new Button(this);
        developer.setText("开发者设置");
        developer.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        buttons.addView(developer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button start = new Button(this);
        start.setText("开始模拟");
        start.setOnClickListener(v -> startMock());
        buttons.addView(start, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button stop = new Button(this);
        stop.setText("停止");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, MockLocationService.class));
            status.setText("状态：已停止");
            refreshDiag();
        });
        buttons.addView(stop, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(buttons);
        setContentView(root);
        requestPermissionsIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiag();
    }

    private void refreshDiag() {
        String msg = getSharedPreferences("mockgps", MODE_PRIVATE).getString("last_diag", "暂无");
        diag.setText("诊断：" + msg);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    private void startMock() {
        try {
            double lat = Double.parseDouble(latInput.getText().toString().trim());
            double lon = Double.parseDouble(lonInput.getText().toString().trim());
            Intent i = new Intent(this, MockLocationService.class);
            i.putExtra("lat", lat);
            i.putExtra("lon", lon);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("状态：正在启动  " + lat + ", " + lon);
            diag.postDelayed(this::refreshDiag, 800);
        } catch (Exception e) {
            Toast.makeText(this, "请输入正确的经纬度", Toast.LENGTH_SHORT).show();
        }
    }

    public class MapBridge {
        @JavascriptInterface
        public void onMapPicked(final double lat, final double lon) {
            runOnUiThread(() -> {
                latInput.setText(String.valueOf(lat));
                lonInput.setText(String.valueOf(lon));
            });
        }
    }
}
