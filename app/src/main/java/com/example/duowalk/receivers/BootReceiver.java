package com.example.duowalk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.duowalk.services.StepCounterService;
import com.example.duowalk.utils.FirebaseUtils;
import com.example.duowalk.utils.ReminderScheduler;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        if (FirebaseUtils.getCurrentUid() != null) {
            Intent serviceIntent = new Intent(context, StepCounterService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }

        // Reschedule reminders after reboot
        ReminderScheduler.scheduleSaved(context);
    }

}
