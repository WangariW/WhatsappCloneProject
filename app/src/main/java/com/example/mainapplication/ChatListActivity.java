package com.example.mainapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DividerItemDecoration;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class ChatListActivity extends AppCompatActivity {

    private RecyclerView rvChatList;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ArrayList<User> userList;
    private UserAdapter userAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.google.firebase.FirebaseApp.initializeApp(this);

        setContentView(R.layout.activity_chat_list);

        rvChatList = findViewById(R.id.rvChatList);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        userList = new ArrayList<>();
        userAdapter = new UserAdapter(userList, this, user -> openChatRoom(user));

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
                    )
                    .addOnFailureListener(e ->
                            System.out.println("Presence update failed: " + e.getMessage()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUserOnlineStatus(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        setUserOnlineStatus(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setUserOnlineStatus(false);
    }
}
