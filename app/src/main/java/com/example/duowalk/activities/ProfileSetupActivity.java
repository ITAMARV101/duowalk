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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileSetupActivity extends AppCompatActivity {

    private TextInputLayout tilUsername, tilPhone;
    private TextInputEditText etUsername, etPhone;
    private MaterialButton btnSaveProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_setup);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootProfileSetup), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupListeners();
    }

    private void initViews() {
        tilUsername = findViewById(R.id.tilUsername);
        tilPhone = findViewById(R.id.tilPhone);

        etUsername = findViewById(R.id.etUsername);
        etPhone = findViewById(R.id.etPhone);

        btnSaveProfile = findViewById(R.id.btnSaveProfile);
    }

    private void setupListeners() {
        btnSaveProfile.setOnClickListener(v -> handleSave());
    }

    private void handleSave() {
        String usernameRaw = getText(etUsername);
        String phoneRaw = getText(etPhone);

        clearErrors();

        if (TextUtils.isEmpty(usernameRaw)) {
            tilUsername.setError("Username is required");
            return;
        }

        // (Optional) basic username rules
        String usernameKey = normalizeUsernameKey(usernameRaw);
        if (usernameKey.length() < 3) {
            tilUsername.setError("Username must be at least 3 characters");
            return;
        }

        if (TextUtils.isEmpty(phoneRaw)) {
            tilPhone.setError("Phone number is required");
            return;
        }

        if (!isPossiblePhone(phoneRaw)) {
            tilPhone.setError("Phone number doesn’t look real");
            return;
        }

        String uid = FirebaseUtils.getCurrentUid();
        if (uid == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveProfile.setEnabled(false);

        String normalizedPhone = normalizePhone(phoneRaw);
        String phoneHash = sha256Hex(normalizedPhone);

        claimUsernameThenPhone(uid, usernameRaw, usernameKey, normalizedPhone, phoneHash);
    }

    // --------- STEP 1: claim username ---------
    private void claimUsernameThenPhone(String uid,
                                        String usernameDisplay,
                                        String usernameKey,
                                        String phoneNormalized,
                                        String phoneHash) {

        DatabaseReference unameRef = FirebaseUtils.usernamesRef.child(usernameKey);

        unameRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                if (currentData.getValue() != null) {
                    // already taken
                    return Transaction.abort();
                }
                currentData.setValue(uid);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    btnSaveProfile.setEnabled(true);
                    Toast.makeText(ProfileSetupActivity.this,
                            "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }

                if (!committed) {
                    btnSaveProfile.setEnabled(true);
                    tilUsername.setError("Username is already taken");
                    return;
                }

                // username claimed -> now claim phone
                claimPhone(uid, usernameDisplay, usernameKey, phoneNormalized, phoneHash);
            }
        });
    }

    // --------- STEP 2: claim phone ---------
    private void claimPhone(String uid,
                            String usernameDisplay,
                            String usernameKey,
                            String phoneNormalized,
                            String phoneHash) {

        DatabaseReference phoneRef = FirebaseUtils.phoneIndexRef.child(phoneHash);

        phoneRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                if (currentData.getValue() != null) {
                    return Transaction.abort(); // already used
                }
                currentData.setValue(uid);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    // rollback username claim
                    releaseUsername(usernameKey, uid);
                    btnSaveProfile.setEnabled(true);
                    Toast.makeText(ProfileSetupActivity.this,
                            "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }

                if (!committed) {
                    // rollback username claim
                    releaseUsername(usernameKey, uid);
                    btnSaveProfile.setEnabled(true);
                    tilPhone.setError("Phone number is already in use");
                    return;
                }

                // both claimed -> save actual profile data
                saveAllData(uid, usernameDisplay, phoneNormalized);
            }
        });
    }

    private void releaseUsername(String usernameKey, String uid) {
        // Only remove if it still equals this uid (simple safety)
        FirebaseUtils.usernamesRef.child(usernameKey).get().addOnSuccessListener(snap -> {
            Object v = snap.getValue();
            if (v != null && uid.equals(String.valueOf(v))) {
                FirebaseUtils.usernamesRef.child(usernameKey).removeValue();
            }
        });
    }

    // --------- STEP 3: save user/private + public profile ---------
    private void saveAllData(String uid, String usernameDisplay, String phoneNormalized) {

        // Private updates under /users/$uid
        Map<String, Object> privateUpdates = new HashMap<>();
        privateUpdates.put("username", usernameDisplay);
        privateUpdates.put("phoneNum", phoneNormalized);

        // Only initialize these if you want defaults on first setup
        privateUpdates.put("steps", 0);
        privateUpdates.put("personalBest", 0);
        privateUpdates.put("streak", 0);

        FirebaseUtils.updateUserPrivateData(uid, privateUpdates, (DatabaseError error, DatabaseReference ref) -> {
            if (error != null) {
                btnSaveProfile.setEnabled(true);
                Toast.makeText(this,
                        "Failed saving user data: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }

            Map<String, Object> publicProfile = new HashMap<>();
            publicProfile.put("username", usernameDisplay);
            publicProfile.put("steps", 0);
            publicProfile.put("personalBest", 0);
            publicProfile.put("streak", 0);

            FirebaseUtils.upsertPublicProfile(uid, publicProfile, (DatabaseError error2, DatabaseReference ref2) -> {
                btnSaveProfile.setEnabled(true);

                if (error2 != null) {
                    Toast.makeText(this,
                            "Failed saving public profile: " + error2.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(this, "Profile completed", Toast.LENGTH_SHORT).show();
                navigateToMain();
            });
        });
    }

    // ---------------- HELPERS ----------------
    private boolean isPossiblePhone(String raw) {
        String phone = raw.replaceAll("[\\s-]", "");
        if (phone.length() < 7 || phone.length() > 15) return false;
        return android.util.Patterns.PHONE.matcher(phone).matches();
    }

    private String normalizeUsernameKey(String username) {
        // Lowercase + trim. You can also remove spaces.
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String raw) {
        // Simple normalization: keep digits and leading +
        String s = raw.trim().replaceAll("[\\s-()]", "");
        // If you want to force digits only:
        // s = s.replaceAll("[^0-9+]", "");
        return s;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // extremely rare
            return String.valueOf(input.hashCode());
        }
    }

    private void clearErrors() {
        tilUsername.setError(null);
        tilPhone.setError(null);
    }

    private String getText(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void navigateToMain() {
        Intent intent = new Intent(ProfileSetupActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
