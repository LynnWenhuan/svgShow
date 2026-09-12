package com.lynn.mockgpscn;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MockLocationService extends Service {
    private static final String CHANNEL_ID = "mockgps";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> mockProviders = new ArrayList<>();
    private final List<String> providerErrors = new ArrayList<>();
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
                .setContentText("正在初始化模拟定位…")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
        startForeground(1001, notification);

        int mockMode = getMockLocationMode();
        if (mockMode != AppOpsManager.MODE_ALLOWED) {
            failAndStop("Mock Location 未授权，AppOp=" + mockMode + "。请重新在开发者选项中选择 Mock GPS CN");
            return;
        }

        setupProvider(LocationManager.GPS_PROVIDER, false, true, false);
        setupProvider(LocationManager.NETWORK_PROVIDER, true, false, true);
        if (Build.VERSION.SDK_INT >= 31) {
            setupProvider(LocationManager.FUSED_PROVIDER, true, false, true);
        }

        if (mockProviders.isEmpty()) {
            String detail = providerErrors.isEmpty() ? "未知错误" : join(providerErrors);
            failAndStop("Provider 注册失败：" + detail);
        } else {
            saveDiag("Mock AppOp=ALLOWED；已注册 Provider：" + join(mockProviders));
            updateNotification("已注册：" + join(mockProviders));
        }
    }

    private int getMockLocationMode() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            return appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), getPackageName());
        } catch (Exception e) {
            providerErrors.add("AppOp检查:" + e.getClass().getSimpleName() + ":" + safeMessage(e));
            return AppOpsManager.MODE_ERRORED;
        }
    }

    private void setupProvider(String provider, boolean requiresNetwork, boolean requiresSatellite, boolean requiresCell) {
        try {
            try { lm.removeTestProvider(provider); } catch (Exception ignored) {}

            if (Build.VERSION.SDK_INT >= 31) {
                ProviderProperties props = new ProviderProperties.Builder()
                        .setHasNetworkRequirement(requiresNetwork)
                        .setHasSatelliteRequirement(requiresSatellite)
                        .setHasCellRequirement(requiresCell)
                        .setHasMonetaryCost(false)
                        .setHasAltitudeSupport(true)
                        .setHasSpeedSupport(true)
                        .setHasBearingSupport(true)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                        .setAccuracy(ProviderProperties.ACCURACY_FINE)
                        .build();
                lm.addTestProvider(provider, props);
            } else {
                lm.addTestProvider(
                        provider,
                        requiresNetwork,
                        requiresSatellite,
                        requiresCell,
                        false,
                        true,
                        true,
                        true,
                        ProviderProperties.POWER_USAGE_LOW,
                        ProviderProperties.ACCURACY_FINE
                );
            }

            lm.setTestProviderEnabled(provider, true);
            if (!mockProviders.contains(provider)) mockProviders.add(provider);
        } catch (Exception e) {
            providerErrors.add(provider + ":" + e.getClass().getSimpleName() + ":" + safeMessage(e));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            lat = intent.getDoubleExtra("lat", 22.543096);
            lon = intent.getDoubleExtra("lon", 114.057865);
        }
        if (!mockProviders.isEmpty()) {
            handler.removeCallbacks(updater);
            handler.post(updater);
        }
        return START_STICKY;
    }

    private boolean pushLocations() {
        if (mockProviders.isEmpty()) return false;

        int successCount = 0;
        List<String> pushErrors = new ArrayList<>();
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
            } catch (Exception e) {
                pushErrors.add(provider + ":" + e.getClass().getSimpleName() + ":" + safeMessage(e));
            }
        }

        if (successCount == 0) {
            failAndStop("位置注入失败：" + join(pushErrors));
            return false;
        }

        String ok = "模拟已生效 " + lat + ", " + lon + "；成功 Provider=" + successCount;
        saveDiag(ok);
        updateNotification(ok);
        return true;
    }

    private void updateNotification(String text) {
        try {
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Mock GPS CN")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setOngoing(true)
                    .build();
            getSystemService(NotificationManager.class).notify(1001, notification);
        } catch (Exception ignored) {}
    }

    private void saveDiag(String message) {
        getSharedPreferences("mockgps", MODE_PRIVATE).edit().putString("last_diag", message).apply();
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? "(无消息)" : e.getMessage();
    }

    private String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private void failAndStop(String message) {
        saveDiag(message);
        updateNotification(message);
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
