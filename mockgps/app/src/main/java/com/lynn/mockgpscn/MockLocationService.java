package com.lynn.mockgpscn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MockLocationService extends Service {
    private static final String CHANNEL_ID = "mockgps";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> mockProviders = new ArrayList<>();
    private LocationManager lm;
    private double lat;
    private double lon;

    private final Runnable updater = new Runnable() {
        @Override public void run() {
            if (pushLocations()) {
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Mock GPS CN")
                .setContentText("模拟定位正在运行")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
        startForeground(1001, notification);

        try {
            setupProvider(LocationManager.GPS_PROVIDER, false, true, false);
            setupProvider(LocationManager.NETWORK_PROVIDER, true, false, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setupProvider(LocationManager.FUSED_PROVIDER, true, true, true);
            }
        } catch (SecurityException e) {
            failAndStop("未获得模拟位置权限，请在开发者选项中选择 Mock GPS CN");
        }
    }

    private void setupProvider(String provider, boolean requiresNetwork, boolean requiresSatellite, boolean requiresCell) {
        try {
            lm.addTestProvider(
                    provider,
                    requiresNetwork,
                    requiresSatellite,
                    requiresCell,
                    false,
                    true,
                    true,
                    true,
                    1,
                    1
            );
            lm.setTestProviderEnabled(provider, true);
            if (!mockProviders.contains(provider)) mockProviders.add(provider);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception ignored) {
            // Some vendor ROMs may refuse one provider. Keep the providers that work.
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            lat = intent.getDoubleExtra("lat", 22.543096);
            lon = intent.getDoubleExtra("lon", 114.057865);
        }
        handler.removeCallbacks(updater);
        handler.post(updater);
        return START_STICKY;
    }

    private boolean pushLocations() {
        if (mockProviders.isEmpty()) {
            failAndStop("没有可用的模拟位置 Provider，请重新选择 Mock GPS CN 为模拟位置信息应用");
            return false;
        }

        int successCount = 0;
        for (String provider : new ArrayList<>(mockProviders)) {
            try {
                Location location = new Location(provider);
                location.setLatitude(lat);
                location.setLongitude(lon);
                location.setAccuracy(2.0f);
                location.setAltitude(10.0);
                location.setSpeed(0.0f);
                location.setBearing(0.0f);
                location.setTime(System.currentTimeMillis());
                location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                lm.setTestProviderLocation(provider, location);
                successCount++;
            } catch (SecurityException e) {
                failAndStop("模拟位置权限失效，请重新在开发者选项中选择 Mock GPS CN");
                return false;
            } catch (Exception ignored) {
            }
        }

        if (successCount == 0) {
            failAndStop("位置注入失败，当前系统没有接受 Mock Location");
            return false;
        }
        return true;
    }

    private void failAndStop(String message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Mock GPS", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(updater);
        for (String provider : new ArrayList<>(mockProviders)) {
            try { lm.setTestProviderEnabled(provider, false); } catch (Exception ignored) {}
            try { lm.removeTestProvider(provider); } catch (Exception ignored) {}
        }
        mockProviders.clear();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
