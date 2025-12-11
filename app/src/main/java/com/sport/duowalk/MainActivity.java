package com.sport.duowalk;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ניווט לכל אחד מהמסכים
        findViewById(R.id.btn_steps).setOnClickListener(v ->
                startActivity(new Intent(this, StepsActivity.class)));

        findViewById(R.id.btn_leaderboard).setOnClickListener(v ->
                startActivity(new Intent(this, LeaderboardActivity.class)));

        findViewById(R.id.btn_friends).setOnClickListener(v ->
                startActivity(new Intent(this, FriendsActivity.class)));

        findViewById(R.id.btn_profile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }
}
