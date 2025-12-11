package com.example.duowalk.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
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

        // ---------- basic checks ----------
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

        // ---------- 1) Try LOGIN ----------
        FirebaseUtils.authFB
                .signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Login success → go to main
                        btnPrimaryAction.setEnabled(true);
                        navigateToMain();
                    } else {
                        // Login failed → check why
                        Exception e = task.getException();

                        if (e instanceof FirebaseAuthInvalidUserException) {
                            // User doesn't exist → SIGN UP instead
                            createAccount(email, phone, password);
                        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            btnPrimaryAction.setEnabled(true);
                            tilPassword.setError("Incorrect password");
                        } else {
                            btnPrimaryAction.setEnabled(true);
                            Toast.makeText(this,
                                    "Login failed: " + (e != null ? e.getMessage() : ""),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // ---------- 2) SIGN UP path if user didn't exist ----------
    private void createAccount(String email, String phone, String password) {
        FirebaseUtils.authFB
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        btnPrimaryAction.setEnabled(true);
                        Toast.makeText(this,
                                "Sign up failed: " +
                                        (task.getException() != null ? task.getException().getMessage() : ""),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    AuthResult result = task.getResult();
                    if (result == null || result.getUser() == null) {
                        btnPrimaryAction.setEnabled(true);
                        Toast.makeText(this, "Unknown error", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = result.getUser().getUid();
                    saveUserData(uid, email, phone);
                });
    }

    // Save user data to Realtime Database (private + public profile)
    private void saveUserData(String uid, String email, String phone) {
        // Private user node
        Map<String, Object> privateData = new HashMap<>();
        privateData.put("uid", uid);
        privateData.put("email", email);
        privateData.put("phoneNum", phone);
        privateData.put("steps", 0);
        privateData.put("personalBest", 0);
        privateData.put("streak", 0);

        FirebaseUtils.createUserPrivateData(uid, privateData, (DatabaseError error, DatabaseReference ref) -> {
            if (error != null) {
                btnPrimaryAction.setEnabled(true);
                Toast.makeText(this,
                        "Failed saving private data: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Public profile (rules: only username, steps, personalBest, streak)
            Map<String, Object> publicProfile = new HashMap<>();
            publicProfile.put("username", email);  // later you can change to a real username
            publicProfile.put("steps", 0);
            publicProfile.put("personalBest", 0);
            publicProfile.put("streak", 0);

            FirebaseUtils.upsertPublicProfile(uid, publicProfile, (DatabaseError error1, DatabaseReference ref1) -> {
                btnPrimaryAction.setEnabled(true);

                if (error1 != null) {
                    Toast.makeText(this,
                            "Failed saving public profile: " + error1.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(this, "Account created", Toast.LENGTH_SHORT).show();
                navigateToMain();
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

    private void navigateToMain() {
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
