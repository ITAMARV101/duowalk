package com.example.duowalk.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class FirebaseUtils {

    public static FirebaseAuth authFB = FirebaseAuth.getInstance();
    public static FirebaseFirestore dbFB = FirebaseFirestore.getInstance();
    public static FirebaseStorage storageFB = FirebaseStorage.getInstance();
    public static DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
    public static DatabaseReference sharedRef = FirebaseDatabase.getInstance().getReference("shared");
}




