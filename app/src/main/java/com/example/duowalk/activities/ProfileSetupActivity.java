package com.example.duowalk.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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

/**
 * ProfileSetupActivity
 *
 * Purpose:
 * - Collect username + phone number after signup/login.
 * - Enforce uniqueness for username + phone using RTDB "index" nodes:
 *      /usernames/{usernameKey} -> uid
 *      /phone_index/{phoneHash} -> uid
 * - Save:
 *      Private user data to /users/{uid}
 *      Public profile to /public_profiles/{uid}
 *
 * IMPORTANT:
 * - We initialize the "steps" structure here so the app UI can safely read it immediately
 *   (even before the StepCounterService runs).
 * - We must rollback claims (username/phone) if a later step fails, otherwise the user
 *   can get "stuck" with taken username/phone even though setup did not complete.
 */
public class ProfileSetupActivity extends AppCompatActivity {

    private TextInputLayout tilUsername, tilPhone;
    private TextInputEditText etUsername, etPhone;
    private MaterialButton btnSaveProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_setup);

        // Apply proper padding for system bars (status/navigation)
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

    /**
     * Validates inputs and starts the 3-step process:
     * 1) Claim username (transaction)
     * 2) Claim phone (transaction)
     * 3) Save user data + public profile
     */
    private void handleSave() {
        String usernameRaw = getText(etUsername);
        String phoneRaw = getText(etPhone);

        clearErrors();

        // ---------- Validate ----------
        if (TextUtils.isEmpty(usernameRaw)) {
            tilUsername.setError("Username is required");
            return;
        }

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

        // Must be logged in
        String uid = FirebaseUtils.getCurrentUid();
        if (uid == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent double-taps
        btnSaveProfile.setEnabled(false);

        // Normalize & hash phone for uniqueness index
        String normalizedPhone = normalizePhone(phoneRaw);
        String phoneHash = sha256Hex(normalizedPhone);

        // Start the claim flow
        claimUsernameThenPhone(uid, usernameRaw, usernameKey, normalizedPhone, phoneHash);
    }

    // =========================================================
    // STEP 1: Claim username (transaction)
    // =========================================================

    /**
     * Claims /usernames/{usernameKey} = uid atomically.
     * If it already exists -> username is taken.
     */
    private void claimUsernameThenPhone(String uid,
                                        String usernameDisplay,
                                        String usernameKey,
                                        String phoneNormalized,
                                        String phoneHash) {

        DatabaseReference unameRef = FirebaseUtils.usernamesRef.child(usernameKey);

        unameRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() != null) {
                    // Someone already claimed this username
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
                            "Error claiming username: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if (!committed) {
                    btnSaveProfile.setEnabled(true);
                    tilUsername.setError("Username is already taken");
                    return;
                }

                // Username claimed -> proceed to claim phone
                claimPhone(uid, usernameDisplay, usernameKey, phoneNormalized, phoneHash);
            }
        });
    }

    // =========================================================
    // STEP 2: Claim phone (transaction)
    // =========================================================

    /**
     * Claims /phone_index/{phoneHash} = uid atomically.
     * If it already exists -> phone is already used.
     *
     * If phone claim fails -> rollback username claim.
     */
    private void claimPhone(String uid,
                            String usernameDisplay,
                            String usernameKey,
                            String phoneNormalized,
                            String phoneHash) {

        DatabaseReference phoneRef = FirebaseUtils.phoneIndexRef.child(phoneHash);

        phoneRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() != null) {
                    return Transaction.abort(); // already used
                }
                currentData.setValue(uid);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    // rollback username claim (since phone failed)
                    releaseUsername(usernameKey, uid);
                    btnSaveProfile.setEnabled(true);
                    Toast.makeText(ProfileSetupActivity.this,
                            "Error claiming phone: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if (!committed) {
                    // rollback username claim
                    releaseUsername(usernameKey, uid);
                    btnSaveProfile.setEnabled(true);
                    tilPhone.setError("Phone number is already in use");
                    return;
                }

                // Both claimed -> now save the actual data
                saveAllData(uid, usernameDisplay, usernameKey, phoneNormalized, phoneHash);
            }
        });
    }

    // =========================================================
    // STEP 3: Save /users + /public_profiles
    // =========================================================

    /**
     * Saves user data to:
     * - /users/{uid} (private)
     * - /public_profiles/{uid} (public)
     *
     * If any save fails -> rollback BOTH index claims (username + phone).
     */
    private void saveAllData(String uid,
                             String usernameDisplay,
                             String usernameKey,
                             String phoneNormalized,
                             String phoneHash) {

        // ---------- Private: /users/{uid} ----------
        Map<String, Object> privateUpdates = new HashMap<>();
        privateUpdates.put("username", usernameDisplay);
        privateUpdates.put("phoneNum", phoneNormalized);

        // Initialize steps schema so UI reads are safe immediately:
        // /users/{uid}/steps/allTime
        // /users/{uid}/steps/lastSync
        // /users/{uid}/steps/today/{yyyy-MM-dd}
        String dateKey = FirebaseUtils.todayKey();
        privateUpdates.put("steps/allTime", 0L);
        privateUpdates.put("steps/lastSync", System.currentTimeMillis());
        privateUpdates.put("steps/today/" + dateKey, 0);

        // Other stats (keep as-is; adjust if your app uses a different structure)
        privateUpdates.put("personalBest", 0);
        privateUpdates.put("streak", 0);

        FirebaseUtils.updateUserPrivateData(uid, privateUpdates, (DatabaseError error, DatabaseReference ref) -> {
            if (error != null) {
                // Rollback claims because the user record wasn't written
                rollbackClaims(usernameKey, phoneHash, uid);

                btnSaveProfile.setEnabled(true);
                Toast.makeText(this,
                        "Failed saving user data: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }

            // ---------- Public: /public_profiles/{uid} ----------
            Map<String, Object> publicProfile = new HashMap<>();
            publicProfile.put("username", usernameDisplay);

            // Leaderboard-friendly fields (simple numbers)
            publicProfile.put("steps", 0);
            publicProfile.put("personalBest", 0);
            publicProfile.put("streak", 0);

            FirebaseUtils.upsertPublicProfile(uid, publicProfile, (DatabaseError error2, DatabaseReference ref2) -> {
                btnSaveProfile.setEnabled(true);

                if (error2 != null) {
                    // Rollback claims because the public profile wasn't written
                    rollbackClaims(usernameKey, phoneHash, uid);

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

    // =========================================================
    // ROLLBACK HELPERS
    // =========================================================

    /**
     * Removes /usernames/{usernameKey} if it still belongs to uid.
     * (Safety: don't delete someone else's claim.)
     */
    private void releaseUsername(String usernameKey, String uid) {
        FirebaseUtils.usernamesRef.child(usernameKey).get().addOnSuccessListener(snap -> {
            Object v = snap.getValue();
            if (v != null && uid.equals(String.valueOf(v))) {
                FirebaseUtils.usernamesRef.child(usernameKey).removeValue();
            }
        });
    }

    /**
     * Removes /phone_index/{phoneHash} if it still belongs to uid.
     */
    private void releasePhone(String phoneHash, String uid) {
        FirebaseUtils.phoneIndexRef.child(phoneHash).get().addOnSuccessListener(snap -> {
            Object v = snap.getValue();
            if (v != null && uid.equals(String.valueOf(v))) {
                FirebaseUtils.phoneIndexRef.child(phoneHash).removeValue();
            }
        });
    }

    /**
     * Rollback both claims (username + phone).
     * Used when later steps (saving user/public data) fail.
     */
    private void rollbackClaims(String usernameKey, String phoneHash, String uid) {
        releaseUsername(usernameKey, uid);
        releasePhone(phoneHash, uid);
    }

    // =========================================================
    // VALIDATION + UTILS
    // =========================================================

    private boolean isPossiblePhone(String raw) {
        String phone = raw.replaceAll("[\\s-]", "");
        if (phone.length() < 7 || phone.length() > 15) return false;
        return android.util.Patterns.PHONE.matcher(phone).matches();
    }

    /**
     * Username key used in /usernames index.
     * Lowercase + trim. (You can also strip spaces if you want.)
     */
    private String normalizeUsernameKey(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Simple phone normalization used before hashing.
     * Removes spaces, dashes, parentheses.
     */
    private String normalizePhone(String raw) {
        return raw.trim().replaceAll("[\\s-()]", "");
    }

    /**
     * SHA-256 hash of normalized phone.
     * Used as a privacy-preserving key in /phone_index.
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
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
