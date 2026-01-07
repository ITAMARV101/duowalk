package com.example.duowalk.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.duowalk.R;
import com.example.duowalk.services.StepCounterService;
import com.example.duowalk.utils.FirebaseUtils;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvUsername;
    private TextView tvPhoneNum;
    private Button btnDeleteUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvUsername = findViewById(R.id.tv_username);
        tvPhoneNum = findViewById(R.id.tv_phonenum);
        btnDeleteUser = findViewById(R.id.btn_delete_user);

        loadUsername();
        loadPhoneNumber();

        btnDeleteUser.setOnClickListener(v -> showDeleteConfirmDialog());

        findViewById(R.id.btn_edit_profile).setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, EditProfileActivity.class)));
    }

    private void loadUsername() {
        String uid = FirebaseUtils.getCurrentUid();
        if (uid == null) return;

        FirebaseUtils.getOnceAt(
                FirebaseUtils.publicProfilesRef,
                uid,
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String username = snapshot.child("username").getValue(String.class);
                        if (username != null && !username.isEmpty()) {
                            tvUsername.setText(username);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                }
        );
    }

    private void loadPhoneNumber() {
        String uid = FirebaseUtils.getCurrentUid();
        if (uid == null) return;

        FirebaseUtils.getOnceAt(
                FirebaseUtils.usersRef,
                uid,
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String phone = snapshot.child("phoneNum").getValue(String.class);
                        if (phone != null && !phone.isEmpty()) {
                            tvPhoneNum.setText(phone);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                }
        );
    }

    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete account")
                .setMessage("Are you sure you want to delete your profile? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteUserAndGoToAuth())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void deleteUserAndGoToAuth() {
        FirebaseUser user = FirebaseUtils.authFB.getCurrentUser();
        String uid = FirebaseUtils.getCurrentUid();

        if (user == null || uid == null) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_LONG).show();
            return;
        }

        // IMPORTANT: stop step service first so it won't recreate /users/{uid}/steps while deleting
        stopService(new Intent(this, StepCounterService.class));

        btnDeleteUser.setEnabled(false);

        FirebaseUtils.deleteAccountFully(uid, user).addOnCompleteListener(task -> {
            btnDeleteUser.setEnabled(true);

            if (task.isSuccessful()) {
                Intent i = new Intent(ProfileActivity.this, AuthActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            } else {
                String msg = (task.getException() != null) ? task.getException().getMessage() : "unknown error";
                Toast.makeText(this, "Delete failed: " + msg, Toast.LENGTH_LONG).show();

                // If you see something like "requires recent login":
                // You must re-authenticate the user (sign in again) then retry delete.
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsername();
        loadPhoneNumber();
    }
}