package com.example.duowalk.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.duowalk.R;
import com.example.duowalk.utils.FirebaseUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;

import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPhone, tilPassword;
    private TextInputEditText etEmail, etPhone, etPassword;
    private MaterialButton btnPrimaryAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootAuth), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupListeners();
    }

    private void initViews() {
        tilEmail = findViewById(R.id.tilEmail);
        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);

        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);

        btnPrimaryAction = findViewById(R.id.btnPrimaryAction);
    }

    private void setupListeners() {
        btnPrimaryAction.setOnClickListener(v -> handleContinue());
    }

    private void handleContinue() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        clearErrors();

        // --------- simple checks ----------
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email");
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            tilPhone.setError("Phone is required");
            return;
        }
        if (!isPossiblePhone(phone)) {
            tilPhone.setError("Phone number doesn’t look real");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            tilPassword.setError("At least 6 characters");
            return;
        }

        btnPrimaryAction.setEnabled(false);

        // --------- Auth: create user with email + password ----------
        FirebaseUtils.authFB
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnPrimaryAction.setEnabled(true);

                    if (!task.isSuccessful()) {
                        Toast.makeText(this,
                                "Auth failed: " +
                                        (task.getException() != null ? task.getException().getMessage() : ""),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    AuthResult result = task.getResult();
                    if (result == null || result.getUser() == null) {
                        Toast.makeText(this, "Unknown error", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = result.getUser().getUid();
                    saveUserData(uid, email, phone);
                });
    }

    // Save to Realtime Database using FirebaseUtils CRUD helpers
    private void saveUserData(String uid, String email, String phone) {
        Map<String, Object> privateData = new HashMap<>();
        privateData.put("uid", uid);
        privateData.put("email", email);
        privateData.put("phoneNum", phone);
        privateData.put("steps", 0);
        privateData.put("personalBest", 0);
        privateData.put("streak", 0);

        FirebaseUtils.createUserPrivateData(uid, privateData, (error, ref) -> {
            if (error != null) {
                Toast.makeText(this,
                        "Failed saving private data: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }

            Map<String, Object> publicProfile = new HashMap<>();
            publicProfile.put("username", email);
            publicProfile.put("steps", 0);
            publicProfile.put("personalBest", 0);
            publicProfile.put("streak", 0);

            FirebaseUtils.upsertPublicProfile(uid, publicProfile, (error1, ref1) -> {
                if (error1 != null) {
                    Toast.makeText(this,
                            "Failed saving public profile: " + error1.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(this, "Account created", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(AuthActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();

            });
        });
    }


    private void clearErrors() {
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilPassword.setError(null);
    }

    // very light “could be real” phone check
    private boolean isPossiblePhone(String raw) {
        if (raw == null) return false;
        String phone = raw.replaceAll("[\\s-]", "");
        if (phone.length() < 7 || phone.length() > 15) return false;
        return android.util.Patterns.PHONE.matcher(phone).matches();
    }
}
