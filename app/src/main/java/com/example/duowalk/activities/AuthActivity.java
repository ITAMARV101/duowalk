package com.example.duowalk.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.duowalk.R;
import com.example.duowalk.utils.FirebaseUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

public class AuthActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
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
        tilPassword = findViewById(R.id.tilPassword);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        btnPrimaryAction = findViewById(R.id.btnPrimaryAction);
    }

    private void setupListeners() {
        btnPrimaryAction.setOnClickListener(v -> handleContinue());
        findViewById(R.id.tvForgotPassword).setOnClickListener(v -> handleForgotPassword());
    }

    private void handleContinue() {
        String email = textOrEmpty(etEmail);
        String password = textOrEmpty(etPassword);

        clearErrors();

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email");
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

        // 1) Try LOGIN first
        FirebaseUtils.authFB
                .signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        btnPrimaryAction.setEnabled(true);
                        navigateToMain();
                        return;
                    }

                    Exception e = task.getException();

                    // If it's clearly wrong password, don't attempt sign-up
                    if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        btnPrimaryAction.setEnabled(true);
                        tilPassword.setError("Incorrect password");
                        return;
                    }

                    // If it's clearly "user not found", we can go directly to sign-up
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        createAccountAndGoToSetup(email, password);
                        return;
                    }

                    // Otherwise: could be enumeration protection / generic auth error.
                    // Try sign-up. If it says email already exists, we know password is wrong.
                    createAccountAndGoToSetup(email, password);
                });
    }

    private void createAccountAndGoToSetup(String email, String password) {
        FirebaseUtils.authFB
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnPrimaryAction.setEnabled(true);

                    if (task.isSuccessful()) {
                        // New user created -> go to profile setup
                        Intent intent = new Intent(AuthActivity.this, ProfileSetupActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    Exception e = task.getException();

                    // Email already exists -> it was a LOGIN attempt with wrong password
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        tilPassword.setError("Incorrect password");
                        return;
                    }

                    Toast.makeText(this,
                            "Auth failed: " + (e != null ? e.getMessage() : ""),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void handleForgotPassword() {
        String email = textOrEmpty(etEmail);
        clearErrors();

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Enter your email first");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email");
            return;
        }

        FirebaseUtils.authFB
                .sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Reset email sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "Failed: " + (task.getException() != null ? task.getException().getMessage() : ""),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToMain() {
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private String textOrEmpty(@Nullable TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }
}
