package com.example.mainapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final ArrayList<User> users;
    private final Context context;
    private final OnUserClickListener listener;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    public UserAdapter(ArrayList<User> users, Context context, OnUserClickListener listener) {
        this.users = users;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.tvUsername.setText(user.getUsername());

        DatabaseReference userStatusRef = FirebaseDatabase.getInstance()
                .getReference("presence")
                .child(user.getUid());

        userStatusRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if ("online".equals(status)) {
                    holder.tvOnlineStatus.setText("Online");
                    holder.tvOnlineStatus.setTextColor(context.getColor(R.color.green));
                    holder.onlineIndicator.setVisibility(View.VISIBLE);
                    animateDot(holder.onlineIndicator);
                } else {
                    holder.onlineIndicator.clearAnimation();
                    holder.onlineIndicator.setVisibility(View.GONE);
                    db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
                        Long lastActive = doc.getLong("lastActive");
                        if (lastActive != null) {
                            holder.tvOnlineStatus.setText(getLastSeenText(lastActive));
                            holder.tvOnlineStatus.setTextColor(context.getColor(R.color.colorTextSecondary));
                        }
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        db.collection("users").document(user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String typingTo = snapshot.getString("typingTo");
                        if (typingTo != null && typingTo.equals(currentUserId)) {
                            holder.tvLastMessage.setText("Typing...");
                            holder.tvLastMessage.setTextColor(context.getColor(R.color.colorAccent));
                            holder.tvTimestamp.setText("");
                            return;
                        } else {
                            holder.tvLastMessage.setTextColor(context.getColor(R.color.colorTextSecondary));
                        }
                    }
                });

        db.collection("chats")
                .whereIn("user1", Arrays.asList(currentUserId, user.getUid()))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (var chatDoc : querySnapshot.getDocuments()) {
                        String u1 = chatDoc.getString("user1");
                        String u2 = chatDoc.getString("user2");
                        if ((u1.equals(currentUserId) && u2.equals(user.getUid())) ||
                                (u2.equals(currentUserId) && u1.equals(user.getUid()))) {

                            String chatId = chatDoc.getId();

                            db.collection("chats").document(chatId)
                                    .collection("messages")
                                    .orderBy("timestamp", Query.Direction.DESCENDING)
                                    .limit(1)
                                    .addSnapshotListener((value, error) -> {
                                        if (error != null || value == null || value.isEmpty()) return;
                                        var msg = value.getDocuments().get(0);
                                        String text = msg.getString("message");
                                        String imageUrl = msg.getString("imageUrl");
                                        Long time = msg.getLong("timestamp");
                                        boolean seen = Boolean.TRUE.equals(msg.getBoolean("seen"));
                                        String senderId = msg.getString("senderId");

                                        if (imageUrl != null && !imageUrl.isEmpty()) {
                                            holder.tvLastMessage.setText("📷 Photo");
                                        } else if (text != null && !text.isEmpty()) {
                                            holder.tvLastMessage.setText(text);
                                        } else {
                                            holder.tvLastMessage.setText("No messages yet");
                                        }

                                        if (time != null) {
                                            holder.tvTimestamp.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault())
                                                    .format(new Date(time)));
                                        }

                                        if (!seen && senderId != null && !senderId.equals(currentUserId)) {
                                            holder.unreadDot.setVisibility(View.VISIBLE);
                                        } else {
                                            holder.unreadDot.setVisibility(View.GONE);
                                        }
                                    });
                        }
                    }
                });

        holder.itemView.setOnClickListener(v -> listener.onUserClick(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername, tvLastMessage, tvTimestamp, tvOnlineStatus;
        ImageView imgProfile;
        View onlineIndicator, unreadDot;

        UserViewHolder(View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvOnlineStatus = itemView.findViewById(R.id.tvOnlineStatus);
            imgProfile = itemView.findViewById(R.id.imgProfile);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            unreadDot = itemView.findViewById(R.id.unreadDot);
        }
    }

    private void animateDot(View dot) {
        AlphaAnimation anim = new AlphaAnimation(0.3f, 1.0f);
        anim.setDuration(1000);
        anim.setRepeatCount(AlphaAnimation.INFINITE);
        anim.setRepeatMode(AlphaAnimation.REVERSE);
        dot.startAnimation(anim);
    }

    private String getLastSeenText(long lastActive) {
        long diff = System.currentTimeMillis() - lastActive;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);

        if (minutes < 1) return "Last seen just now";
        else if (minutes < 60) return "Last seen " + minutes + " min ago";
        else {
            long hours = minutes / 60;
            if (hours < 24) return "Last seen " + hours + " hr ago";
            else {
                long days = hours / 24;
                return "Last seen " + days + " day" + (days > 1 ? "s" : "") + " ago";
            }
        }
    }
}
