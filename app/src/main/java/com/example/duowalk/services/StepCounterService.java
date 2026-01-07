package com.example.duowalk.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.duowalk.utils.FirebaseUtils;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class StepCounterService extends Service implements SensorEventListener {

    // ======= SharedPreferences =======
    private static final String PREFS = "steps_prefs";
    private static final String K_TODAY_STEPS = "today_steps";
    private static final String K_ALL_TIME_STEPS = "all_time_steps";
    private static final String K_TODAY_DATE = "today_date";
    private static final String K_LAST_SENSOR_VALUE = "last_sensor_value";

    // ======= Foreground notification =======
    private static final String CHANNEL_ID = "steps_channel";
    private static final int NOTIF_ID = 1001;

    // ======= Sync interval =======
    private static final long SYNC_EVERY_MS = 4000;

    private SharedPreferences sp;
    private SensorManager sensorManager;
    private Sensor stepCounterSensor;

    private Handler handler;
    private Runnable syncRunnable;

    private FirebaseAuth.AuthStateListener authListener;
    private boolean isTracking = false;

    @Override
    public void onCreate() {
        super.onCreate();

        sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Foreground must start quickly after startForegroundService()
        startForegroundNotification("Starting step tracking...");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepCounterSensor = (sensorManager != null)
                ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                : null;

        handler = new Handler(Looper.getMainLooper());

        // Run sync every 4 seconds (only uploads if logged in)
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                ensureTodayNotStale();
                syncToFirebaseIfLoggedIn();
                handler.postDelayed(this, SYNC_EVERY_MS);
            }
        };
        handler.postDelayed(syncRunnable, SYNC_EVERY_MS);

        // Track login/logout in real time
        authListener = firebaseAuth -> {
            String uid = FirebaseUtils.getCurrentUid();
            if (uid != null) {
                // Logged in -> start tracking
                startTrackingIfNeeded();
                updateNotificationText("Tracking steps (logged in)");
            } else {
                // Logged out -> stop tracking + stop uploading
                stopTrackingIfNeeded();
                updateNotificationText("Login required to track steps");
                // Option: stop the service entirely on logout:
                // stopSelf();
            }
        };

        FirebaseUtils.authFB.addAuthStateListener(authListener);

        // Initial state
        if (FirebaseUtils.getCurrentUid() != null) {
            startTrackingIfNeeded();
            updateNotificationText("Tracking steps (logged in)");
        } else {
            stopTrackingIfNeeded();
            updateNotificationText("Login required to track steps");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Keep service alive if killed
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (FirebaseUtils.authFB != null && authListener != null) {
            FirebaseUtils.authFB.removeAuthStateListener(authListener);
        }

        stopTrackingIfNeeded();

        if (handler != null && syncRunnable != null) {
            handler.removeCallbacks(syncRunnable);
        }
    }

    // Unbound service => return null
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // =========================
    // Step Tracking
    // =========================

    private void startTrackingIfNeeded() {
        if (isTracking) return;
        if (sensorManager == null || stepCounterSensor == null) {
            updateNotificationText("Step sensor not available");
            return;
        }

        ensureTodayNotStale();
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
        isTracking = true;
    }

    private void stopTrackingIfNeeded() {
        if (!isTracking) return;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        isTracking = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Only count steps if logged in
        if (FirebaseUtils.getCurrentUid() == null) return;

        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;

        ensureTodayNotStale();

        float sensorValue = event.values[0]; // total steps since boot
        float last = sp.getFloat(K_LAST_SENSOR_VALUE, -1f);

        SharedPreferences.Editor ed = sp.edit();

        if (last < 0f) {
            // First reading today / after reset / after service starts
            ed.putFloat(K_LAST_SENSOR_VALUE, sensorValue).apply();
            return;
        }

        // Handle reboot / sensor reset (value can go down)
        if (sensorValue < last) {
            // Set new baseline, do not add steps
            ed.putFloat(K_LAST_SENSOR_VALUE, sensorValue).apply();
            return;
        }

        int delta = (int) Math.floor(sensorValue - last);
        if (delta > 0) {
            int today = sp.getInt(K_TODAY_STEPS, 0) + delta;
            long allTime = sp.getLong(K_ALL_TIME_STEPS, 0L) + delta;

            ed.putInt(K_TODAY_STEPS, today);
            ed.putLong(K_ALL_TIME_STEPS, allTime);
        }

        ed.putFloat(K_LAST_SENSOR_VALUE, sensorValue);
        ed.apply();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // =========================
    // Daily reset
    // =========================

    private String todayKey() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    private void ensureTodayNotStale() {
        String now = todayKey();
        String saved = sp.getString(K_TODAY_DATE, null);

        if (saved == null) {
            sp.edit().putString(K_TODAY_DATE, now).apply();
            return;
        }

        if (!saved.equals(now)) {
            // New day: reset ONLY today steps, keep all-time
            sp.edit()
                    .putString(K_TODAY_DATE, now)
                    .putInt(K_TODAY_STEPS, 0)
                    .remove(K_LAST_SENSOR_VALUE) // avoid huge delta across day boundary
                    .apply();
        }
    }

    // =========================
    // Firebase Sync (every 4 sec)
    // =========================

    private void syncToFirebaseIfLoggedIn() {
        String uid = FirebaseUtils.getCurrentUid();
        if (uid == null) return;

        int today = sp.getInt(K_TODAY_STEPS, 0);
        long allTime = sp.getLong(K_ALL_TIME_STEPS, 0L);
        String date = sp.getString(K_TODAY_DATE, todayKey());

        FirebaseUtils.saveSteps(uid, date, today, allTime);
    }

    // =========================
    // Foreground Notification
    // =========================

    private void startForegroundNotification(String text) {
        createNotificationChannelIfNeeded();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DuoWalk Step Tracking")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();

        startForeground(NOTIF_ID, notification);
    }

    private void updateNotificationText(String text) {
        createNotificationChannelIfNeeded();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DuoWalk Step Tracking")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, notification);
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
            if (existing != null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Step Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(channel);
        }
    }
}
