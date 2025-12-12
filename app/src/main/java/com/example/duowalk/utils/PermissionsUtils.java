package com.example.duowalk.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PermissionsUtils {

    private PermissionsUtils() {}

    // ---- Request codes ----
    public static final int REQ_LOCATION_FOREGROUND = 1001;
    public static final int REQ_LOCATION_BACKGROUND = 1002;
    public static final int REQ_CAMERA = 1003;
    public static final int REQ_GALLERY = 1004;
    public static final int REQ_CONTACTS = 1005;
    public static final int REQ_PHONE_DIRECT_CALL = 1006;
    public static final int REQ_ACTIVITY_RECOGNITION = 1007;
    public static final int REQ_NOTIFICATIONS = 1008;
    public static final int REQ_BLUETOOTH = 1009;

    // -------------------------------------------------------------------------
    // Permission groups (return only what is relevant per Android version)
    // -------------------------------------------------------------------------

    /** Foreground location for maps/nearby features */
    @NonNull
    public static String[] locationForegroundPermissions() {
        return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
    }

    /**
     * Background location (Android 10+). Request only AFTER foreground is granted
     * and only if you have a strong feature requiring it.
     */
    @NonNull
    public static String[] locationBackgroundPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // 29+
            return new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION};
        }
        return new String[0];
    }

    /** Camera */
    @NonNull
    public static String[] cameraPermissions() {
        return new String[]{Manifest.permission.CAMERA};
    }

    /** Gallery / photos picker */
    @NonNull
    public static String[] galleryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // 33+
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        }
        // API 24-32
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    /** Contacts (read only) */
    @NonNull
    public static String[] contactsPermissions() {
        return new String[]{Manifest.permission.READ_CONTACTS};
    }

    /** Activity recognition for steps (Android 10+) */
    @NonNull
    public static String[] activityRecognitionPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // 29+
            return new String[]{Manifest.permission.ACTIVITY_RECOGNITION};
        }
        return new String[0];
    }

    /** Notifications permission (Android 13+) */
    @NonNull
    public static String[] notificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // 33+
            return new String[]{Manifest.permission.POST_NOTIFICATIONS};
        }
        return new String[0];
    }

    /** Optional: Bluetooth permissions for Android 12+ (wearables/BLE scanning) */
    @NonNull
    public static String[] bluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // 31+
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        }
        return new String[0];
    }

    /**
     * Direct phone call permission.
     * Prefer ACTION_DIAL (no permission) unless you truly place calls automatically.
     */
    @NonNull
    public static String[] phoneDirectCallPermissions() {
        return new String[]{Manifest.permission.CALL_PHONE};
    }

    // -------------------------------------------------------------------------
    // Combined sets for your app screens/features (deduplicated)
    // -------------------------------------------------------------------------

    /** Main map: location (foreground) + notifications (optional) */
    @NonNull
    public static String[] permissionsForMainMap() {
        return combine(locationForegroundPermissions(), notificationPermissions());
    }

    /** Steps: activity recognition (+ notifications optional) */
    @NonNull
    public static String[] permissionsForSteps() {
        return combine(activityRecognitionPermissions(), notificationPermissions());
    }

    /** Profile photo: camera + gallery */
    @NonNull
    public static String[] permissionsForProfilePhoto() {
        return combine(cameraPermissions(), galleryPermissions());
    }

    /** Friends: contacts */
    @NonNull
    public static String[] permissionsForFriends() {
        return contactsPermissions();
    }

    /** Optional feature: BLE integration */
    @NonNull
    public static String[] permissionsForBluetooth() {
        return bluetoothPermissions();
    }

    // -------------------------------------------------------------------------
    // Core helpers (no duplication)
    // -------------------------------------------------------------------------

    /** Combine multiple permission arrays into one (deduplicated, stable order). */
    @NonNull
    public static String[] combine(@NonNull String[]... groups) {
        Set<String> set = new LinkedHashSet<>();
        for (String[] g : groups) {
            if (g == null) continue;
            set.addAll(Arrays.asList(g));
        }
        set.remove(null);
        set.remove("");
        return set.toArray(new String[0]);
    }

    public static boolean hasPermissions(@NonNull Context context, @NonNull String... permissions) {
        for (String p : permissions) {
            if (p == null || p.isEmpty()) continue;
            if (ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public static String[] getMissingPermissions(@NonNull Context context, @NonNull String... permissions) {
        List<String> missing = new ArrayList<>();
        for (String p : permissions) {
            if (p == null || p.isEmpty()) continue;
            if (ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        return missing.toArray(new String[0]);
    }

    /**
     * Request only missing permissions. Returns true if already granted (nothing requested).
     */
    public static boolean requestMissingPermissions(
            @NonNull Activity activity,
            int requestCode,
            @NonNull String... permissions
    ) {
        String[] missing = getMissingPermissions(activity, permissions);
        if (missing.length == 0) return true;
        ActivityCompat.requestPermissions(activity, missing, requestCode);
        return false;
    }

    /** Returns true if any permission needs rationale UI. */
    public static boolean shouldShowRationale(@NonNull Activity activity, @NonNull String... permissions) {
        for (String p : permissions) {
            if (p == null || p.isEmpty()) continue;
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, p)) {
                return true;
            }
        }
        return false;
    }

    /** Helper for onRequestPermissionsResult: check if all were granted. */
    public static boolean allGranted(@NonNull int[] grantResults) {
        if (grantResults.length == 0) return false;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /** Open app settings (useful when user chose "Don't ask again"). */
    public static void openAppSettings(@NonNull Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Phone helpers (avoid permission unless needed)
    // -------------------------------------------------------------------------

    /** Dialer intent (no permission required). */
    public static void openDialer(@NonNull Activity activity, @NonNull String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
        activity.startActivity(intent);
    }

    /**
     * Direct call intent (requires CALL_PHONE permission).
     * Use only if your UX really needs calling immediately.
     */
    public static void startDirectCall(@NonNull Activity activity, @NonNull String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
        activity.startActivity(intent);
    }
}
