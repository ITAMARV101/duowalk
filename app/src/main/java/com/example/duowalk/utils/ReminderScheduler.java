package com.example.duowalk.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.duowalk.receivers.ReminderReceiver;

import java.util.Calendar;

public class ReminderScheduler {

    private static final String PREFS = "reminders_prefs";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DAILY_HOUR = "daily_hour";
    private static final String KEY_DAILY_MIN = "daily_min";
    private static final String KEY_WEEKLY_DAY = "weekly_day";
    private static final String KEY_WEEKLY_HOUR = "weekly_hour";
    private static final String KEY_WEEKLY_MIN = "weekly_min";

    private static final int REQ_DAILY = 100;
    private static final int REQ_WEEKLY = 200;

    // Defaults (change later from UI)
    private static final int DEFAULT_DAILY_HOUR = 19;
    private static final int DEFAULT_DAILY_MIN = 0;

    private static final int DEFAULT_WEEKLY_DAY = Calendar.SUNDAY;
    private static final int DEFAULT_WEEKLY_HOUR = 11;
    private static final int DEFAULT_WEEKLY_MIN = 0;

    // ---------- Public API ----------

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();

        if (enabled) {
            scheduleSaved(context);
        } else {
            cancelAll(context);
        }
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    /** Call this from BootReceiver to restore alarms after reboot */
    public static void scheduleSaved(Context context) {
        if (!isEnabled(context)) return;

        var sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        int dh = sp.getInt(KEY_DAILY_HOUR, DEFAULT_DAILY_HOUR);
        int dm = sp.getInt(KEY_DAILY_MIN, DEFAULT_DAILY_MIN);

        int wd = sp.getInt(KEY_WEEKLY_DAY, DEFAULT_WEEKLY_DAY);
        int wh = sp.getInt(KEY_WEEKLY_HOUR, DEFAULT_WEEKLY_HOUR);
        int wm = sp.getInt(KEY_WEEKLY_MIN, DEFAULT_WEEKLY_MIN);

        scheduleDaily(context, dh, dm);
        scheduleWeekly(context, wd, wh, wm);
    }

    /** Called by receiver after firing so next one is scheduled */
    public static void rescheduleFromReceiver(Context context, String type) {
        if (!isEnabled(context)) return;

        var sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if ("weekly".equals(type)) {
            int wd = sp.getInt(KEY_WEEKLY_DAY, DEFAULT_WEEKLY_DAY);
            int wh = sp.getInt(KEY_WEEKLY_HOUR, DEFAULT_WEEKLY_HOUR);
            int wm = sp.getInt(KEY_WEEKLY_MIN, DEFAULT_WEEKLY_MIN);
            scheduleWeekly(context, wd, wh, wm);
        } else {
            int dh = sp.getInt(KEY_DAILY_HOUR, DEFAULT_DAILY_HOUR);
            int dm = sp.getInt(KEY_DAILY_MIN, DEFAULT_DAILY_MIN);
            scheduleDaily(context, dh, dm);
        }
    }

    public static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        am.cancel(buildPendingIntent(context, REQ_DAILY, "daily"));
        am.cancel(buildPendingIntent(context, REQ_WEEKLY, "weekly"));
    }

    // ---------- Scheduling ----------

    private static void scheduleDaily(Context context, int hour, int minute) {
        saveDailyTime(context, hour, minute);

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(context, REQ_DAILY, "daily");
        am.cancel(pi);

        setExactCompat(am, c.getTimeInMillis(), pi);
    }

    private static void scheduleWeekly(Context context, int dayOfWeek, int hour, int minute) {
        saveWeeklyTime(context, dayOfWeek, hour, minute);

        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.WEEK_OF_YEAR, 1);
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(context, REQ_WEEKLY, "weekly");
        am.cancel(pi);

        setExactCompat(am, c.getTimeInMillis(), pi);
    }

    private static void setExactCompat(AlarmManager am, long triggerAtMillis, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    private static PendingIntent buildPendingIntent(Context context, int requestCode, String type) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.putExtra(ReminderReceiver.EXTRA_TYPE, type);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        return PendingIntent.getBroadcast(context, requestCode, i, flags);
    }

    private static void saveDailyTime(Context context, int hour, int minute) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_DAILY_HOUR, hour)
                .putInt(KEY_DAILY_MIN, minute)
                .apply();
    }

    private static void saveWeeklyTime(Context context, int dayOfWeek, int hour, int minute) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_WEEKLY_DAY, dayOfWeek)
                .putInt(KEY_WEEKLY_HOUR, hour)
                .putInt(KEY_WEEKLY_MIN, minute)
                .apply();
    }
}
