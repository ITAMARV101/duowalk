package com.sport.duowalk;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Switch notifSwitch = findViewById(R.id.switch_notifications);
        Switch darkModeSwitch = findViewById(R.id.switch_darkmode);

        notifSwitch.setOnCheckedChangeListener((b, isChecked) ->
                Toast.makeText(this, isChecked ? "Notifications enabled" : "Notifications disabled", Toast.LENGTH_SHORT).show());

        darkModeSwitch.setOnCheckedChangeListener((b, isChecked) ->
                Toast.makeText(this, isChecked ? "Dark mode activated" : "Light mode activated", Toast.LENGTH_SHORT).show());
    }
}
