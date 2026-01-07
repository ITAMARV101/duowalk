package com.example.duowalk.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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

public class EditProfileActivity extends AppCompatActivity {

    private TextInputLayout tilUsername, tilPhone;
    private TextInputEditText etUsername, etPhone;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        tilUsername = findViewById(R.id.tilUsername);
        tilPhone = findViewById(R.id.tilPhone);
        etUsername = findViewById(R.id.etUsername);
        etPhone = findViewById(R.id.etPhone);
        btnSave = findViewById(R.id.btnSave);

        prefillCurrentData();

        btnSave.setOnClickListener(v -> handleSave());
    }

    // ---------------- PREFILL ----------------

    private void prefillCurrentData() {
        String uid = FirebaseUtils.getCurrentUid();
        if (uid == null) return;

        // Username (public)
        FirebaseUtils.publicProfilesRef.child(uid).get().addOnSuccessListener(snap -> {
            String username = snap.child("username").getValue(String.class);
            if (username != null) etUsername.setText(username);
        });

        // Phone (private)
        FirebaseUtils.usersRef.child(uid).get().addOnSuccessListener(snap -> {
            String phone = snap.child("phoneNum").getValue(String.class);
            if (phone != null) etPhone.setText(phone);
        });
    }

    // ---------------- SAVE ----------------

    private void handleSave() {
        String uid = FirebaseUtils.getCurrentUid();
        if (uid == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        tilUsername.setError(null);
        tilPhone.setError(null);

        String newUsername = text(etUsername);
        String newPhoneRaw = text(etPhone);

        // Validate username
        if (TextUtils.isEmpty(newUsername)) {
            tilUsername.setError("Username is required");
            return;
        }

        String newUsernameKey = normalizeUsernameKey(newUsername);
        if (newUsernameKey.length() < 3) {
            tilUsername.setError("Username must be at least 3 characters");
            return;
        }

        // Validate phone
        if (TextUtils.isEmpty(newPhoneRaw)) {
            tilPhone.setError("Phone number is required");
            return;
        }

        if (!isPossiblePhone(newPhoneRaw)) {
            tilPhone.setError("Phone number doesn’t look real");
            return;
        }

        String newPhoneNormalized = normalizePhone(newPhoneRaw);
        String newPhoneHash = sha256Hex(newPhoneNormalized);

        btnSave.setEnabled(false);

        // Read current stored values so we can detect what changed
        FirebaseUtils.usersRef.child(uid).get().addOnSuccessListener(userSnap -> {

            String oldUsername = userSnap.child("username").getValue(String.class);
            String oldPhone = userSnap.child("phoneNum").getValue(String.class);

            String oldUsernameKey = (oldUsername != null) ? normalizeUsernameKey(oldUsername) : null;
            String oldPhoneNormalized = (oldPhone != null) ? normalizePhone(oldPhone) : null;
            String oldPhoneHash = (oldPhoneNormalized != null) ? sha256Hex(oldPhoneNormalized) : null;

            boolean usernameChanged = (oldUsernameKey == null) || !oldUsernameKey.equals(newUsernameKey);
            boolean phoneChanged = (oldPhoneHash == null) || !oldPhoneHash.equals(newPhoneHash);

            // Nothing changed -> done
            if (!usernameChanged && !phoneChanged) {
                btnSave.setEnabled(true);
                Toast.makeText(this, "No changes", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // Step 1: claim username ONLY if changed
            if (usernameChanged) {
                claimUsername(uid, newUsernameKey, okUser -> {
                    if (!okUser) {
                        btnSave.setEnabled(true);
                        tilUsername.setError("Username is already taken");
                        return;
                    }
                    // Step 2: claim phone ONLY if changed
                    claimPhoneIfNeededThenUpdate(uid, phoneChanged, newPhoneHash,
                            newUsernameKey, oldUsernameKey, oldPhoneHash,
                            newUsername, newPhoneNormalized);
                });
            } else {
                // Username unchanged -> skip claim
                claimPhoneIfNeededThenUpdate(uid, phoneChanged, newPhoneHash,
                        null, oldUsernameKey, oldPhoneHash,
                        newUsername, newPhoneNormalized);
            }

        }).addOnFailureListener(e -> {
            btnSave.setEnabled(true);
            Toast.makeText(this, "Failed reading current user: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    // If phone didn't change, skip phone claim
    private void claimPhoneIfNeededThenUpdate(String uid,
                                              boolean phoneChanged,
                                              String newPhoneHash,
                                              String claimedUsernameKeyOrNull,
                                              String oldUsernameKey,
                                              String oldPhoneHash,
                                              String newUsername,
                                              String newPhoneNormalized) {

        if (phoneChanged) {
            claimPhone(uid, newPhoneHash, okPhone -> {
                if (!okPhone) {
                    // rollback username only if we claimed it in this run
                    if (claimedUsernameKeyOrNull != null) {
                        releaseUsernameIfOwned(uid, claimedUsernameKeyOrNull);
                    }
                    btnSave.setEnabled(true);
                    tilPhone.setError("Phone number is already in use");
                    return;
                }

                updateProfileData(uid, newUsername, newPhoneNormalized,
                        (claimedUsernameKeyOrNull != null) ? claimedUsernameKeyOrNull : oldUsernameKey,
                        newPhoneHash,
                        oldUsernameKey, oldPhoneHash);
            });
        } else {
            // phone unchanged -> just update data
            updateProfileData(uid, newUsername, newPhoneNormalized,
                    (claimedUsernameKeyOrNull != null) ? claimedUsernameKeyOrNull : oldUsernameKey,
                    oldPhoneHash,
                    oldUsernameKey, oldPhoneHash);
        }
    }

    // Update /users and /public_profiles, then release old claims if changed
    private void updateProfileData(String uid,
                                   String newUsername,
                                   String newPhoneNormalized,
                                   String finalUsernameKey,
                                   String finalPhoneHash,
                                   String oldUsernameKey,
                                   String oldPhoneHash) {

        Map<String, Object> privateUpdates = new HashMap<>();
        privateUpdates.put("username", newUsername);
        privateUpdates.put("phoneNum", newPhoneNormalized);

        // Optional but helpful (doesn't require FirebaseUtils changes)
        privateUpdates.put("usernameKey", finalUsernameKey);
        privateUpdates.put("phoneHash", finalPhoneHash);

        FirebaseUtils.usersRef.child(uid).updateChildren(privateUpdates, (err, ref) -> {
            if (err != null) {
                // rollback NEW claims if they were new
                if (oldUsernameKey == null || !oldUsernameKey.equals(finalUsernameKey)) {
                    releaseUsernameIfOwned(uid, finalUsernameKey);
                }
                if (oldPhoneHash == null || !oldPhoneHash.equals(finalPhoneHash)) {
                    releasePhoneIfOwned(uid, finalPhoneHash);
                }

                btnSave.setEnabled(true);
                Toast.makeText(this, "Failed updating user: " + err.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            FirebaseUtils.publicProfilesRef.child(uid).child("username").setValue(newUsername, (err2, ref2) -> {
                if (err2 != null) {
                    if (oldUsernameKey == null || !oldUsernameKey.equals(finalUsernameKey)) {
                        releaseUsernameIfOwned(uid, finalUsernameKey);
                    }
                    if (oldPhoneHash == null || !oldPhoneHash.equals(finalPhoneHash)) {
                        releasePhoneIfOwned(uid, finalPhoneHash);
                    }

                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Failed updating public profile: " + err2.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }

                // Release OLD claims if they changed
                if (oldUsernameKey != null && !oldUsernameKey.equals(finalUsernameKey)) {
                    releaseUsernameIfOwned(uid, oldUsernameKey);
                }
                if (oldPhoneHash != null && !oldPhoneHash.equals(finalPhoneHash)) {
                    releasePhoneIfOwned(uid, oldPhoneHash);
                }

                btnSave.setEnabled(true);
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    // ---------------- CLAIM / RELEASE (INDEXES) ----------------

    private void claimUsername(String uid, String usernameKey, Callback cb) {
        DatabaseReference ref = FirebaseUtils.usernamesRef.child(usernameKey);
        ref.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Object v = currentData.getValue();
                if (v != null && !uid.equals(String.valueOf(v))) {
                    return Transaction.abort();
                }
                currentData.setValue(uid);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                cb.done(error == null && committed);
            }
        });
    }

    private void claimPhone(String uid, String phoneHash, Callback cb) {
        DatabaseReference ref = FirebaseUtils.phoneIndexRef.child(phoneHash);
        ref.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Object v = currentData.getValue();
                if (v != null && !uid.equals(String.valueOf(v))) {
                    return Transaction.abort();
                }
                currentData.setValue(uid);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                cb.done(error == null && committed);
            }
        });
    }

    private void releaseUsernameIfOwned(String uid, String usernameKey) {
        if (usernameKey == null || usernameKey.isEmpty()) return;
        FirebaseUtils.usernamesRef.child(usernameKey).get().addOnSuccessListener(snap -> {
            Object v = snap.getValue();
            if (v != null && uid.equals(String.valueOf(v))) {
                FirebaseUtils.usernamesRef.child(usernameKey).removeValue();
            }
        });
    }

    private void releasePhoneIfOwned(String uid, String phoneHash) {
        if (phoneHash == null || phoneHash.isEmpty()) return;
        FirebaseUtils.phoneIndexRef.child(phoneHash).get().addOnSuccessListener(snap -> {
            Object v = snap.getValue();
            if (v != null && uid.equals(String.valueOf(v))) {
                FirebaseUtils.phoneIndexRef.child(phoneHash).removeValue();
            }
        });
    }

    // ---------------- HELPERS ----------------

    private boolean isPossiblePhone(String raw) {
        String phone = raw.replaceAll("[\\s-]", "");
        if (phone.length() < 7 || phone.length() > 15) return false;
        return android.util.Patterns.PHONE.matcher(phone).matches();
    }

    private String normalizeUsernameKey(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String raw) {
        return raw.trim().replaceAll("[\\s-()]", "");
    }

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

    private String text(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    interface Callback {
        void done(boolean ok);
    }
}
