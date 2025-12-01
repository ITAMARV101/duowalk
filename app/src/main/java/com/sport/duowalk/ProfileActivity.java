package com.sport.duowalk;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ImageButton btnEdit = findViewById(R.id.btn_edit_profile);
        btnEdit.setOnClickListener(v ->
                Toast.makeText(this, "Edit profile coming soon!", Toast.LENGTH_SHORT).show());
    }
}
