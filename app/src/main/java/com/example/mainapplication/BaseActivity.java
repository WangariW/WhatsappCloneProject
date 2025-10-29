package com.example.mainapplication;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

public abstract class BaseActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    @Override
    protected void onResume() {
        super.onResume();
        updateUserPresence(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateUserPresence(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateUserPresence(false);
    }

    private void updateUserPresence(boolean isOnline) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        long now = System.currentTimeMillis();


        db.collection("users")
                .document(uid)
                .update("online", isOnline, "lastActive", now);


        FirebaseDatabase.getInstance()
                .getReference("presence")
                .child(uid)
                .setValue(isOnline ? "online" : "offline");
    }
}
