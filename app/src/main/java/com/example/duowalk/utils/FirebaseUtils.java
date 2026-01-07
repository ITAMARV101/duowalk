package com.example.duowalk.utils;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * FirebaseUtils
 *
 * Centralized helper class for Firebase:
 * - Auth
 * - Realtime Database
 * - Firestore
 * - Storage
 *
 * Includes convenience helpers for:
 * - /users (private)
 * - /public_profiles (public)
 * - /tasks
 * - username + phone uniqueness indexes
 * - step tracking (daily + all-time)
 * - account deletion (RTDB + Auth)
 */
public class FirebaseUtils {

    // =========================================================
    // CORE INSTANCES
    // =========================================================
    public static FirebaseAuth authFB = FirebaseAuth.getInstance();
    public static FirebaseFirestore dbFB = FirebaseFirestore.getInstance();
    public static FirebaseStorage storageFB = FirebaseStorage.getInstance();
    public static FirebaseDatabase rtDb = FirebaseDatabase.getInstance();

    // =========================================================
    // ROOT NODES (RTDB)
    // =========================================================
    public static DatabaseReference usersRef          = rtDb.getReference("users");
    public static DatabaseReference publicProfilesRef = rtDb.getReference("public_profiles");
    public static DatabaseReference tasksRef          = rtDb.getReference("tasks");
    public static DatabaseReference usernamesRef      = rtDb.getReference("usernames");
    public static DatabaseReference phoneIndexRef     = rtDb.getReference("phone_index");

    // =========================================================
    // AUTH HELPERS
    // =========================================================
    public static String getCurrentUid() {
        return authFB.getCurrentUser() != null
                ? authFB.getCurrentUser().getUid()
                : null;
    }

    // =========================================================
    // DATE HELPERS
    // =========================================================

    /**
     * @return Today's date key "yyyy-MM-dd" in the device local timezone.
     * Used for: /users/{uid}/steps/today/{dateKey}
     */
    public static String todayKey() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    // =========================================================
    // GENERIC HELPERS (work for any node)
    // =========================================================

    /** Create/replace: parent/{childKey} = data */
    public static void setAt(
            DatabaseReference parent,
            String childKey,
            Object data,
            DatabaseReference.CompletionListener listener
    ) {
        parent.child(childKey).setValue(data, listener);
    }

    /** Partial update: parent/{childKey} merge updates */
    public static void updateAt(
            DatabaseReference parent,
            String childKey,
            Map<String, Object> updates,
            DatabaseReference.CompletionListener listener
    ) {
        parent.child(childKey).updateChildren(updates, listener);
    }

    /** Delete: parent/{childKey} */
    public static void deleteAt(
            DatabaseReference parent,
            String childKey,
            DatabaseReference.CompletionListener listener
    ) {
        parent.child(childKey).removeValue(listener);
    }

    /** Read once: parent/{childKey} */
    public static void getOnceAt(
            DatabaseReference parent,
            String childKey,
            ValueEventListener listener
    ) {
        parent.child(childKey).addListenerForSingleValueEvent(listener);
    }

    /** Push a new auto-id child under parent */
    public static DatabaseReference pushNew(
            DatabaseReference parent,
            Object data,
            DatabaseReference.CompletionListener listener
    ) {
        DatabaseReference newRef = parent.push();
        newRef.setValue(data, listener);
        return newRef;
    }

    // =========================================================
    // USERS (private): /users/{uid}
    // =========================================================

    public static void createUserPrivateData(
            String uid,
            Map<String, Object> data,
            DatabaseReference.CompletionListener listener
    ) {
        setAt(usersRef, uid, data, listener);
    }

    public static void updateUserPrivateData(
            String uid,
            Map<String, Object> updates,
            DatabaseReference.CompletionListener listener
    ) {
        updateAt(usersRef, uid, updates, listener);
    }

    public static void deleteUserPrivateData(
            String uid,
            DatabaseReference.CompletionListener listener
    ) {
        deleteAt(usersRef, uid, listener);
    }

    // =========================================================
    // PUBLIC PROFILES: /public_profiles/{uid}
    // =========================================================

    /** Create/replace the whole public profile */
    public static void upsertPublicProfile(
            String uid,
            Map<String, Object> data,
            DatabaseReference.CompletionListener listener
    ) {
        setAt(publicProfilesRef, uid, data, listener);
    }

    /** Update specific fields in the public profile */
    public static void updatePublicProfile(
            String uid,
            Map<String, Object> updates,
            DatabaseReference.CompletionListener listener
    ) {
        updateAt(publicProfilesRef, uid, updates, listener);
    }

    /** Delete the public profile */
    public static void deletePublicProfile(
            String uid,
            DatabaseReference.CompletionListener listener
    ) {
        deleteAt(publicProfilesRef, uid, listener);
    }

    // =========================================================
    // TASKS: /tasks
    // =========================================================

    public static DatabaseReference createTask(
            Map<String, Object> data,
            DatabaseReference.CompletionListener listener
    ) {
        return pushNew(tasksRef, data, listener);
    }

    public static void updateTask(
            String taskId,
            Map<String, Object> updates,
            DatabaseReference.CompletionListener listener
    ) {
        updateAt(tasksRef, taskId, updates, listener);
    }

    public static void deleteTask(
            String taskId,
            DatabaseReference.CompletionListener listener
    ) {
        deleteAt(tasksRef, taskId, listener);
    }

    // =========================================================
    // STEPS HELPERS (daily + all-time)
    // =========================================================
    //
    // /users/{uid}/steps/
    //    allTime: long
    //    lastSync: timestamp
    //    today/{yyyy-MM-dd}: int

    public static void saveSteps(String uid, String dateKey, int todaySteps, long allTimeSteps) {
        if (uid == null || dateKey == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("steps/today/" + dateKey, todaySteps);
        updates.put("steps/allTime", allTimeSteps);
        updates.put("steps/lastSync", System.currentTimeMillis());

        usersRef.child(uid).updateChildren(updates);
    }

    public static void saveTodaySteps(String uid, String dateKey, int todaySteps) {
        if (uid == null || dateKey == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("steps/today/" + dateKey, todaySteps);
        updates.put("steps/lastSync", System.currentTimeMillis());

        usersRef.child(uid).updateChildren(updates);
    }

    public static void saveAllTimeSteps(String uid, long allTimeSteps) {
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("steps/allTime", allTimeSteps);
        updates.put("steps/lastSync", System.currentTimeMillis());

        usersRef.child(uid).updateChildren(updates);
    }

    // =========================================================
    // ACCOUNT DELETE HELPERS
    // =========================================================

    /**
     * Deletes ALL RTDB data for the user:
     *  - /users/{uid}               (includes steps)
     *  - /public_profiles/{uid}
     *  - /usernames/{usernameKey}
     *  - /phone_index/{phoneHash}
     *
     * Reads /users/{uid} first to compute usernameKey and phoneHash.
     */
    public static Task<Void> deleteAccountRtdb(String uid) {
        return usersRef.child(uid).get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Exception e = task.getException();
                throw (e != null) ? e : new Exception("Failed to read user data for delete");
            }

            DataSnapshot snap = task.getResult();

            String username = snap.child("username").getValue(String.class);
            String phoneNum = snap.child("phoneNum").getValue(String.class);

            String usernameKey = (username != null) ? normalizeUsernameKeyStatic(username) : null;

            String phoneNormalized = (phoneNum != null) ? normalizePhoneStatic(phoneNum) : null;
            String phoneHash = (phoneNormalized != null && !phoneNormalized.isEmpty())
                    ? sha256HexStatic(phoneNormalized)
                    : null;

            Map<String, Object> updates = new HashMap<>();
            updates.put("users/" + uid, null);
            updates.put("public_profiles/" + uid, null);

            if (usernameKey != null && !usernameKey.isEmpty()) {
                updates.put("usernames/" + usernameKey, null);
            }

            if (phoneHash != null && !phoneHash.isEmpty()) {
                updates.put("phone_index/" + phoneHash, null);
            }

            return rtDb.getReference().updateChildren(updates);
        });
    }

    /**
     * Full deletion:
     * 1) delete RTDB
     * 2) delete Auth user
     * 3) sign out
     *
     * NOTE: Auth delete can fail with "requires recent login".
     */
    public static Task<Void> deleteAccountFully(String uid, FirebaseUser user) {
        if (uid == null || user == null) {
            return Tasks.forException(new IllegalArgumentException("Missing uid or user"));
        }

        return deleteAccountRtdb(uid)
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) {
                        Exception e = t.getException();
                        return Tasks.forException(e != null ? e : new Exception("RTDB delete failed"));
                    }
                    return user.delete();
                })
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) {
                        Exception e = t.getException();
                        return Tasks.forException(e != null ? e : new Exception("Auth delete failed"));
                    }
                    authFB.signOut();
                    return Tasks.forResult(null);
                });
    }

    // =========================================================
    // INTERNAL HELPERS (used by delete + indexes)
    // =========================================================

    private static String normalizeUsernameKeyStatic(String username) {
        return username.trim().toLowerCase();
    }

    private static String normalizePhoneStatic(String raw) {
        return raw.trim().replaceAll("[\\s-()]", "");
    }

    private static String sha256HexStatic(String input) {
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
}
