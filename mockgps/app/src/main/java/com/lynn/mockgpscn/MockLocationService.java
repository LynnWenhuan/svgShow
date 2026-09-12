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

public class MockLocationService extends Service {
    private static final String CHANNEL_ID = "mockgps";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private double lat;
    private double lon;

    private final Runnable updater = new Runnable() {
        @Override public void run() {
            pushLocation();
            handler.postDelayed(this, 1000);
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
                .build();
        startForeground(1001, notification);
        try {
            lm.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true, 0, 5);
        } catch (Exception ignored) {}
        try { lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true); } catch (Exception ignored) {}
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

    private void pushLocation() {
        try {
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(lat);
            location.setLongitude(lon);
            location.setAccuracy(3f);
            location.setAltitude(10.0);
            location.setTime(System.currentTimeMillis());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, location);
        } catch (SecurityException e) {
            Toast.makeText(this, "请在开发者选项中把 Mock GPS CN 设为模拟位置信息应用", Toast.LENGTH_LONG).show();
            stopSelf();
        } catch (Exception e) {
            stopSelf();
        }
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
        try { lm.clearTestProviderLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        try { lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
