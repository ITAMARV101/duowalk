package com.example.duowalk.activities;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.duowalk.R;

public class FriendsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        findViewById(R.id.btn_add_friend).setOnClickListener(v ->
                Toast.makeText(this, "Friend request sent!", Toast.LENGTH_SHORT).show());
    }
}
