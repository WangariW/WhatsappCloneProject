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

        if (auth.getCurrentUser() != null) {
            presenceRef = FirebaseDatabase.getInstance()
                    .getReference("presence")
                    .child(auth.getCurrentUser().getUid());
            presenceRef.setValue("online");
            presenceRef.onDisconnect().setValue("offline");
        }

        userList = new ArrayList<>();
        userAdapter = new UserAdapter(userList, this, this::openChatRoom);

        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvChatList.setAdapter(userAdapter);

        loadUsers();
    }

    private void loadUsers() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

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
                        for (DocumentSnapshot doc : value) {
                            User user = doc.toObject(User.class);
                            if (user != null && user.getUid() != null && !user.getUid().equals(currentUserId)) {
                                userList.add(user);
                            }
                        }
                    }

                    userAdapter.notifyDataSetChanged();
                });
    }

    private String getChatId(String sender, String receiver) {
        return sender.compareTo(receiver) < 0
                ? sender + "_" + receiver
                : receiver + "_" + sender;
    }

    private void openChatRoom(User user) {
        Intent intent = new Intent(this, ChatRoomActivity.class);
        intent.putExtra("userId", user.getUid());
        intent.putExtra("username", user.getUsername());
        startActivity(intent);
    }

    private void setUserOnlineStatus(boolean isOnline) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .update(
                            "online", isOnline,
                            "lastActive", System.currentTimeMillis()
                    );
        }
    }

    private void performLogout() {
        isLoggingOut = true;

        if (auth.getCurrentUser() != null) {
            db.collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .update("online", false, "lastActive", System.currentTimeMillis());

            FirebaseDatabase.getInstance()
                    .getReference("presence")
                    .child(auth.getCurrentUser().getUid())
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
        if (!isLoggingOut && presenceRef != null) presenceRef.setValue("online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isLoggingOut && presenceRef != null) presenceRef.setValue("offline");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isLoggingOut && presenceRef != null) presenceRef.setValue("offline");
    }
}
