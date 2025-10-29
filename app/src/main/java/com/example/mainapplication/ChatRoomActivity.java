package com.example.mainapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ChatRoomActivity extends BaseActivity {

    private TextView tvChatWith, tvTypingStatus;
    private RecyclerView rvMessages;
    private EditText etMessageInput;
    private ImageButton btnSend;
    private ImageView btnAttachImage;

    private MessageAdapter adapter;
    private ArrayList<Message> messageList;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private StorageReference storageRef;

    private String receiverId, senderId, receiverName;
    private String chatId;

    private static final int CAMERA_PERMISSION_CODE = 200;
    private static final int PICK_IMAGE_REQUEST = 101;
    private static final int CAPTURE_IMAGE_REQUEST = 102;

    private Uri cameraImageUri;

    private DocumentReference currentUserRef;
    private ListenerRegistration typingListener;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private final long TYPING_TIMEOUT_MS = 1500;

    private final Runnable clearTypingRunnable = () -> {
        if (currentUserRef != null) currentUserRef.update("typingTo", "");
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.google.firebase.FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_chat_room);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Chat");

        tvChatWith = findViewById(R.id.tvChatWith);
        tvTypingStatus = findViewById(R.id.tvTypingStatus);
        rvMessages = findViewById(R.id.rvMessages);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSend = findViewById(R.id.btnSend);
        btnAttachImage = findViewById(R.id.btnAttachImage);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference("chat_images");

        receiverId = getIntent().getStringExtra("userId");
        receiverName = getIntent().getStringExtra("username");
        senderId = auth.getCurrentUser().getUid();

        if (receiverId == null) {
            Toast.makeText(this, "Chat user not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        chatId = getChatId(senderId, receiverId);
        getSupportActionBar().setTitle(receiverName != null ? receiverName : "Chat with user");
        tvChatWith.setText("Chat with " + receiverName);

        DocumentReference userRef = db.collection("users").document(receiverId);
        userRef.addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;
            String typingTo = snapshot.getString("typingTo");
            Boolean isOnline = snapshot.getBoolean("online");
            Long lastActive = snapshot.getLong("lastActive");

            if (typingTo != null && typingTo.equals(senderId)) {
                tvTypingStatus.setText("Typing...");
            } else if (isOnline != null && isOnline) {
                tvTypingStatus.setText("Online");
            } else if (lastActive != null) {
                tvTypingStatus.setText("Last seen " + getTimeAgo(lastActive));
            } else {
                tvTypingStatus.setText("");
            }
        });

        messageList = new ArrayList<>();
        adapter = new MessageAdapter(messageList);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        currentUserRef = db.collection("users").document(senderId);

        loadMessages();
        listenForTypingStatus();
        handleTypingIndicator();

        btnSend.setOnClickListener(v -> sendTextMessage());
        btnAttachImage.setOnClickListener(v -> showImageSourceDialog());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showImageSourceDialog() {
        String[] options = {"Take Photo", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Send image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) checkCameraPermission();
                    else openGallery();
                })
                .show();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE
            );
        } else {
            openCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void openCamera() {
        try {
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                File photoFile = File.createTempFile(
                        "IMG_" + System.currentTimeMillis(),
                        ".jpg",
                        getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                );
                cameraImageUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        photoFile
                );
                intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri);
                startActivityForResult(intent, CAPTURE_IMAGE_REQUEST);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to open camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
            uploadImageToFirebase(data.getData());
        } else if (requestCode == CAPTURE_IMAGE_REQUEST && cameraImageUri != null) {
            uploadImageToFirebase(cameraImageUri);
        }
    }

    private void uploadImageToFirebase(Uri uri) {
        if (uri == null) return;

        StorageReference fileRef = storageRef.child(System.currentTimeMillis() + ".jpg");
        fileRef.putFile(uri)
                .addOnSuccessListener(taskSnapshot ->
                        fileRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            sendImageMessage(downloadUri.toString());
                            Toast.makeText(this, "Image sent!", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void sendTextMessage() {
        String text = etMessageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        DocumentReference msgRef = db.collection("chats").document(chatId)
                .collection("messages").document();

        Message message = new Message(senderId, receiverId, text, System.currentTimeMillis());
        message.setId(msgRef.getId());
        message.setSeen(false);

        msgRef.set(message)
                .addOnSuccessListener(aVoid -> {
                    etMessageInput.setText("");
                    typingHandler.removeCallbacks(clearTypingRunnable);
                    if (currentUserRef != null) currentUserRef.update("typingTo", "");
                    updateLastMessageForUsers(text);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void sendImageMessage(String imageUrl) {
        DocumentReference msgRef = db.collection("chats").document(chatId)
                .collection("messages").document();

        Message message = new Message(senderId, receiverId, "", System.currentTimeMillis());
        message.setId(msgRef.getId());
        message.setImageUrl(imageUrl);
        message.setSeen(false);

        msgRef.set(message)
                .addOnSuccessListener(aVoid -> updateLastMessageForUsers("📷 Photo"));
    }

    private void updateLastMessageForUsers(String textOrPhotoLabel) {
        long now = System.currentTimeMillis();
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", textOrPhotoLabel);
        updates.put("lastMessageTime", now);

        db.collection("users").document(senderId).update(updates);
        db.collection("users").document(receiverId).update(updates);
    }

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
                                Message msg = dc.getDocument().toObject(Message.class);
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
                        rvMessages.smoothScrollToPosition(messageList.size() - 1);
                    }
                });
    }

    private void handleTypingIndicator() {
        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                typingHandler.removeCallbacks(clearTypingRunnable);
                if (s.length() > 0) {
                    if (currentUserRef != null) currentUserRef.update("typingTo", receiverId);
                    typingHandler.postDelayed(clearTypingRunnable, TYPING_TIMEOUT_MS);
                } else if (currentUserRef != null) currentUserRef.update("typingTo", "");
            }
        });
    }

    private void listenForTypingStatus() {
        if (receiverId == null) return;
        DocumentReference receiverRef = db.collection("users").document(receiverId);
        typingListener = receiverRef.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) return;
            String typingTo = snapshot.getString("typingTo");
            if (typingTo != null && typingTo.equals(senderId)) {
                tvTypingStatus.setText("Typing...");
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
