package com.example.duowalk.receivers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.duowalk.R;
import com.example.duowalk.utils.NotificationUtils;
import com.example.duowalk.utils.ReminderScheduler;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_TYPE = "type"; // "daily" / "weekly" / "test"
    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String type = intent.getStringExtra(EXTRA_TYPE);
        Log.d(TAG, "onReceive fired, type=" + type);

        NotificationUtils.ensureChannel(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted. Skipping notify.");
                return;
            }
        }

        String title = "הגיע הזמן לזוז!";
        String text  = "צא להליכה קצרה 👟";

        if ("weekly".equals(type)) {
            title = "תזכורת שבועית";
            text  = "בוא נשמור על רצף צעדים השבוע 💪";
        } else if ("test".equals(type)) {
            title = "Test notification";
            text  = "If you see this, alarms + notifications work ✅";
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground) // אפשר לשנות לאייקון התראה אמיתי
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        int id = "weekly".equals(type) ? 200 : ("test".equals(type) ? 999 : 100);
        NotificationManagerCompat.from(context).notify(id, builder.build());

        // Reschedule ONLY daily/weekly (not test)
        if ("daily".equals(type) || "weekly".equals(type)) {
            ReminderScheduler.rescheduleFromReceiver(context, type);
        }
    }
}
