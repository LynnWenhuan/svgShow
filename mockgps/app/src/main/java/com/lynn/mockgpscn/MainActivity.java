package com.lynn.mockgpscn;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private EditText latInput;
    private EditText lonInput;
    private TextView status;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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

        WebView map = new WebView(this);
        map.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
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
        });
        buttons.addView(stop, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(buttons);
        setContentView(root);
        requestPermissionsIfNeeded();
    }

    private void requestPermissionsIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
        }
    }

    private void startMock() {
        try {
            double lat = Double.parseDouble(latInput.getText().toString().trim());
            double lon = Double.parseDouble(lonInput.getText().toString().trim());
            Intent i = new Intent(this, MockLocationService.class);
            i.putExtra("lat", lat);
            i.putExtra("lon", lon);
            ContextCompat.startForegroundService(this, i);
            status.setText("状态：模拟中  " + lat + ", " + lon);
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
