package com.example.mainapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

public class ChatRoomActivity extends AppCompatActivity {

    private TextView tvChatWith, tvTypingStatus;
    private RecyclerView rvMessages;
    private EditText etMessageInput;
    private Button btnSend;

    private MessageAdapter adapter;
    private ArrayList<Message> messageList;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String receiverId, senderId, receiverName;
    private String chatId;

    // Typing indicator
    private DocumentReference currentUserRef;
    private ListenerRegistration typingListener;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private final long TYPING_TIMEOUT_MS = 1500;
    private final Runnable clearTypingRunnable = () -> {
        if (currentUserRef != null) {
            currentUserRef.update("typingTo", "")
                    .addOnFailureListener(e -> {/* ignore */});
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.google.firebase.FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_chat_room);

        tvChatWith = findViewById(R.id.tvChatWith);
        tvTypingStatus = findViewById(R.id.tvTypingStatus);
        rvMessages = findViewById(R.id.rvMessages);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSend = findViewById(R.id.btnSend);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();


        receiverId = getIntent().getStringExtra("userId");
        receiverName = getIntent().getStringExtra("username");
        senderId = auth.getCurrentUser().getUid();

        if (receiverId == null) {
            Toast.makeText(this, "Chat user not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        chatId = getChatId(senderId, receiverId);

        tvChatWith.setText("Chat with " + receiverName);

        DocumentReference userRef = db.collection("users").document(receiverId);
        userRef.addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;

            Boolean isOnline = snapshot.getBoolean("online");
            Long lastActive = snapshot.getLong("lastActive");

            if (isOnline != null && isOnline) {
                tvTypingStatus.setText("Online");
            } else if (lastActive != null) {
                tvTypingStatus.setText("Last seen " + getTimeAgo(lastActive));
            } else {
                tvTypingStatus.setText("");
            }
        });

        tvTypingStatus.setText("");

        // RecyclerView setup
        messageList = new ArrayList<>();
        adapter = new MessageAdapter(messageList);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        currentUserRef = db.collection("users").document(senderId);

        // Listeners
        loadMessages();
        listenForTypingStatus();
        handleTypingIndicator();
        btnSend.setOnClickListener(v -> sendMessage());
    }

    /** Real-time Firestore listener for messages */
    private void loadMessages() {
        db.collection("chats").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((QuerySnapshot value, com.google.firebase.firestore.FirebaseFirestoreException error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        for (DocumentChange dc : value.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                Message msg= dc.getDocument().toObject(Message.class);

                                msg.setId(dc.getDocument().getId());

                                messageList.add(msg);

                                if (!msg.getSenderId().equals(senderId) && !msg.isSeen()) {
                                    db.collection("chats").document(chatId)
                                            .collection("messages").document(msg.getId())
                                            .update("seen", true);
                                }
                            }
                        }
                        adapter.notifyDataSetChanged();
                        if (!messageList.isEmpty()) {
                            rvMessages.smoothScrollToPosition(messageList.size() - 1);
                        }
                    }
                });
    }

    private void sendMessage() {
        String text = etMessageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        DocumentReference msgRef = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(); // generate unique ID

        Message message = new Message(senderId, receiverId, text, System.currentTimeMillis());
        message.setId(msgRef.getId());
        message.setSeen(false);

        msgRef.set(message)
                .addOnSuccessListener(aVoid -> {
                    etMessageInput.setText("");
                    typingHandler.removeCallbacks(clearTypingRunnable);
                    if (currentUserRef != null) currentUserRef.update("typingTo", "");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    /** Updates typingTo field while user types */
    private void handleTypingIndicator() {
        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                typingHandler.removeCallbacks(clearTypingRunnable);

                if (s.length() > 0) {
                    if (currentUserRef != null) {
                        currentUserRef.update("typingTo", receiverId)
                                .addOnFailureListener(e -> currentUserRef.set(new User(senderId, "", "")));
                    }
                    typingHandler.postDelayed(clearTypingRunnable, TYPING_TIMEOUT_MS);
                } else {
                    if (currentUserRef != null) currentUserRef.update("typingTo", "");
                }
            }
        });
    }

    /** Listens to the other user’s typingTo field */
    private void listenForTypingStatus() {
        if (receiverId == null) return;
        DocumentReference receiverRef = db.collection("users").document(receiverId);

        typingListener = receiverRef.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) return;
            String typingTo = snapshot.getString("typingTo");
            if (typingTo != null && typingTo.equals(senderId)) {
                tvTypingStatus.setText("Typing...");
            } else {
                tvTypingStatus.setText("");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (typingListener != null) typingListener.remove();
        typingHandler.removeCallbacks(clearTypingRunnable);
        if (currentUserRef != null) currentUserRef.update("typingTo", "");
    }


    private String getChatId(String sender, String receiver) {
        return sender.compareTo(receiver) < 0
                ? sender + "_" + receiver
                : receiver + "_" + sender;
    }

    private String getTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long mins = diff / 60000;
        if (mins < 1) return "just now";
        if (mins < 60) return mins + " min ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + " h ago";
        return (hrs / 24) + " d ago";
    }
}
