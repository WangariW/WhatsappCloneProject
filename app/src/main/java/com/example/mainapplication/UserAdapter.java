package com.example.mainapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

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

        db.collection("users").document(user.getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        Boolean online = snapshot.getBoolean("online");
                        Long lastActive = snapshot.getLong("lastActive");

                        if (online != null && online) {
                            holder.tvOnlineStatus.setText("Online");
                            holder.tvOnlineStatus.setTextColor(context.getColor(R.color.green));
                        } else if (lastActive != null) {
                            holder.tvOnlineStatus.setText("Last seen " + getTimeAgo(lastActive));
                            holder.tvOnlineStatus.setTextColor(context.getColor(R.color.gray));
                        } else {
                            holder.tvOnlineStatus.setText("Offline");
                            holder.tvOnlineStatus.setTextColor(context.getColor(R.color.gray));
                        }
                    }
                });

        holder.tvLastMessage.setText("Loading...");
        holder.tvTimestamp.setText("");

        // --- Fetch the latest message between current user and this user ---
        String chatId = getChatId(currentUserId, user.getUid());
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || value.isEmpty()) {
                        holder.tvLastMessage.setText("No messages yet");
                        holder.tvTimestamp.setText("");
                        return;
                    }

                    Message lastMessage = value.getDocuments().get(0).toObject(Message.class);
                    if (lastMessage == null) return;

                    String prefix = lastMessage.getSenderId().equals(currentUserId)
                            ? "You: "
                            : "";

                    holder.tvLastMessage.setText(prefix + lastMessage.getMessage());

                    // --- Format and display timestamp ---
                    String time = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                            .format(new Date(lastMessage.getTimestamp()));
                    holder.tvTimestamp.setText(time);
                });

        // --- Real-time typing indicator ---
        db.collection("users").document(user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String typingTo = snapshot.getString("typingTo");
                        if (typingTo != null && typingTo.equals(currentUserId)) {
                            holder.tvLastMessage.setText("Typing...");
                            holder.tvTimestamp.setText("");
                        }
                    }
                });

        // --- On user click ---
        holder.itemView.setOnClickListener(v -> listener.onUserClick(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername, tvLastMessage, tvTimestamp, tvOnlineStatus;
        ImageView imgProfile;

        UserViewHolder(View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvOnlineStatus = itemView.findViewById(R.id.tvOnlineStatus);
            imgProfile = itemView.findViewById(R.id.imgProfile);
        }
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
