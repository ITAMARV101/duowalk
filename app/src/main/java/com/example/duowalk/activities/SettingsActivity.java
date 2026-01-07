package com.example.duowalk.activities;

import android.os.Build;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.duowalk.R;
import com.example.duowalk.utils.PermissionsUtils;
import com.example.duowalk.utils.ReminderScheduler;

public class SettingsActivity extends AppCompatActivity {

    private Switch notifSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        notifSwitch = findViewById(R.id.switch_notifications);
        Switch darkModeSwitch = findViewById(R.id.switch_darkmode);

        // Load saved state
        notifSwitch.setChecked(ReminderScheduler.isEnabled(this));

        notifSwitch.setOnCheckedChangeListener((b, isChecked) -> {
            if (isChecked) {
                // Ask permission first on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && !PermissionsUtils.hasPermissions(this, PermissionsUtils.notificationPermissions())) {
                    PermissionsUtils.requestMissingPermissions(
                            this,
                            PermissionsUtils.REQ_NOTIFICATIONS,
                            PermissionsUtils.notificationPermissions()
                    );
                    // don't enable yet; wait for result
                    notifSwitch.setChecked(false);
                    return;
                }

                ReminderScheduler.setEnabled(this, true);
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                ReminderScheduler.setEnabled(this, false);
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show();
            }
        });

        darkModeSwitch.setOnCheckedChangeListener((b, isChecked) ->
                Toast.makeText(this, isChecked ? "Dark mode activated" : "Light mode activated", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionsUtils.REQ_NOTIFICATIONS) {
            if (PermissionsUtils.allGranted(grantResults)) {
                ReminderScheduler.setEnabled(this, true);
                notifSwitch.setChecked(true);
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
