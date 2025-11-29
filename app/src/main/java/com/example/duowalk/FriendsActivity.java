package com.example.duowalk;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
public class FriendsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        int x = 0;

        findViewById(R.id.btn_add_friend).setOnClickListener(v ->
                Toast.makeText(this, "Friend request sent!", Toast.LENGTH_SHORT).show());
    }
}
