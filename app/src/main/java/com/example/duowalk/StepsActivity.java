package com.example.duowalk;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class StepsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_steps);

        Button btn = findViewById(R.id.btn_start_walk);
        btn.setOnClickListener(v ->
                Toast.makeText(this, "Started a walk", Toast.LENGTH_SHORT).show());
    }
}
