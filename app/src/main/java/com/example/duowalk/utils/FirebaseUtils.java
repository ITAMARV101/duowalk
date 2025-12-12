package com.example.duowalk.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.util.Map;

public class FirebaseUtils {

    // ---------- CORE INSTANCES ----------
    public static FirebaseAuth authFB = FirebaseAuth.getInstance();
    public static FirebaseFirestore dbFB = FirebaseFirestore.getInstance();
    public static FirebaseStorage storageFB = FirebaseStorage.getInstance();
    public static FirebaseDatabase rtDb = FirebaseDatabase.getInstance();

    // ---------- ROOT NODES ----------
    public static DatabaseReference usersRef          = rtDb.getReference("users");
    public static DatabaseReference publicProfilesRef = rtDb.getReference("public_profiles");
    public static DatabaseReference tasksRef          = rtDb.getReference("tasks");

    public static DatabaseReference usernamesRef      = rtDb.getReference("usernames");
    public static DatabaseReference phoneIndexRef     = rtDb.getReference("phone_index");



    // ---------- COMMON ----------
    public static String getCurrentUid() {
        return authFB.getCurrentUser() != null
                ? authFB.getCurrentUser().getUid()
                : null;
    }

    // =========================================================
    //  GENERIC HELPERS (work for any node)
    // =========================================================

    /**
     * Set (create/replace) data at parent/childKey
     */
    public static void setAt(
            DatabaseReference parent,
            String childKey,
            Object data,
            DatabaseReference.CompletionListener listener
    ) {
        parent.child(childKey).setValue(data, listener);
    }

    /**
     * Update (partial) data at parent/childKey
     */
    public static void updateAt(
            DatabaseReference parent,
            String childKey,
            Map<String, Object> updates,
            DatabaseReference.CompletionListener listener
    ) {
        parent.child(childKey).updateChildren(updates, listener);
    }

    /**
     * Delete data at parent/childKey
     */
    public static void deleteAt(
            DatabaseReference parent,
            String childKey,
            DatabaseReference.CompletionListener listener
    ) {
        parent.child(childKey).removeValue(listener);
    }

    /**
     * Read once from parent/childKey
     */
    public static void getOnceAt(
            DatabaseReference parent,
            String childKey,
            ValueEventListener listener
    ) {
        parent.child(childKey).addListenerForSingleValueEvent(listener);
    }

    /**
     * Create a new child with push() under a parent node (e.g., tasks)
     */
    public static DatabaseReference pushNew(
            DatabaseReference parent,
            Object data,
            DatabaseReference.CompletionListener listener
    ) {
        DatabaseReference newRef = parent.push();
        newRef.setValue(data, listener);
        return newRef;
    }

    // =================================================================
    //  THIN CONVENIENCE HELPERS  (optional)
    //  These just call the generic ones so your Activity code is nicer.
    // =================================================================

    // ---------- USERS ----------
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

    // ---------- PUBLIC PROFILES ----------
    public static void upsertPublicProfile(
            String uid,
            Map<String, Object> data,
            DatabaseReference.CompletionListener listener
    ) {
        setAt(publicProfilesRef, uid, data, listener);
    }

    public static void updatePublicProfile(
            String uid,
            Map<String, Object> updates,
            DatabaseReference.CompletionListener listener
    ) {
        updateAt(publicProfilesRef, uid, updates, listener);
    }

    public static void deletePublicProfile(
            String uid,
            DatabaseReference.CompletionListener listener
    ) {
        deleteAt(publicProfilesRef, uid, listener);
    }

    // ---------- TASKS ----------
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
}
