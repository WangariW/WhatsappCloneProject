package com.example.mainapplication;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DividerItemDecoration;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

public class ChatListActivity extends BaseActivity {

    private RecyclerView rvChatList;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ArrayList<User> userList;
    private UserAdapter userAdapter;
    private DatabaseReference presenceRef;
    private boolean isLoggingOut = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.google.firebase.FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_chat_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                performLogout();
                return true;
            }
            return false;
        });

        rvChatList = findViewById(R.id.rvChatList);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // --- Presence handling
        presenceRef = FirebaseDatabase.getInstance()
                .getReference("presence")
                .child(currentUser.getUid());
        presenceRef.setValue("online");
        presenceRef.onDisconnect().setValue("offline");

        // --- Recycler setup
        userList = new ArrayList<>();
        userAdapter = new UserAdapter(userList, this, this::openChatRoom);
        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvChatList.setAdapter(userAdapter);

        loadUsers();
    }


    private void loadUsers() {
        String currentUserId = auth.getCurrentUser().getUid();

        db.collection("users")
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Listen failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    userList.clear();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            User user = doc.toObject(User.class);
                            if (user != null && user.getUid() != null && !user.getUid().equals(currentUserId)) {
                                userList.add(user);
                            }
                        }
                    }

                    userAdapter.notifyDataSetChanged();
                });
    }


    private void openChatRoom(User user) {
        if (user == null || user.getUid() == null) {
            Toast.makeText(this, "User data missing", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ChatRoomActivity.class);
        intent.putExtra("userId", user.getUid());

        // Fallback logic for missing username
        String safeName = (user.getUsername() != null && !user.getUsername().trim().isEmpty())
                ? user.getUsername()
                : (user.getEmail() != null ? user.getEmail() : "Chat User");

        intent.putExtra("username", safeName);
        startActivity(intent);
    }


    private void performLogout() {
        isLoggingOut = true;
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();

            // update Firestore
            db.collection("users").document(uid)
                    .update("online", false, "lastActive", System.currentTimeMillis());

            // update Realtime DB presence
            FirebaseDatabase.getInstance()
                    .getReference("presence")
                    .child(uid)
                    .setValue("offline");
        }

        auth.signOut();

        GoogleSignInClient gsc = GoogleSignIn.getClient(
                this,
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
        );

        gsc.signOut().addOnCompleteListener(task -> {
            Intent intent = new Intent(ChatListActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isLoggingOut && presenceRef != null)
            presenceRef.setValue("online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isLoggingOut && presenceRef != null)
            presenceRef.setValue("offline");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isLoggingOut && presenceRef != null)
            presenceRef.setValue("offline");
    }
}
