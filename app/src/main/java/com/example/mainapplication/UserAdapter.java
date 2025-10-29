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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

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

        // Presence (Realtime DB)
        DatabaseReference userStatusRef = FirebaseDatabase.getInstance()
                .getReference("presence")
                .child(user.getUid());

        userStatusRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if ("online".equals(status)) {
                    holder.tvOnlineStatus.setText("Online");
                    holder.tvOnlineStatus.setTextColor(context.getColor(R.color.green));
                    animateDot(holder.onlineIndicator, true);
                } else {
                    holder.tvOnlineStatus.setText("Offline");
                    holder.tvOnlineStatus.setTextColor(context.getColor(R.color.gray));
                    animateDot(holder.onlineIndicator, false);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Typing indicator (optional)
        db.collection("users").document(user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String typingTo = snapshot.getString("typingTo");
                        if (typingTo != null && typingTo.equals(currentUserId)) {
                            holder.tvLastMessage.setText("Typing...");
                            holder.tvTimestamp.setText("");
                            return;
                        }
                    }
                    // Bind lastMessage + time from user document
                    String lm = user.getLastMessage();
                    holder.tvLastMessage.setText(lm == null || lm.isEmpty() ? "No messages yet" : lm);

                    long t = user.getLastMessageTime();
                    if (t > 0) {
                        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                                .format(new Date(t));
                        holder.tvTimestamp.setText(time);
                    } else {
                        holder.tvTimestamp.setText("");
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
        View onlineIndicator;

        UserViewHolder(View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvOnlineStatus = itemView.findViewById(R.id.tvOnlineStatus);
            imgProfile = itemView.findViewById(R.id.imgProfile);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
        }
    }

    private void animateDot(View dot, boolean show) {
        if (show) {
            dot.setVisibility(View.VISIBLE);
            AlphaAnimation anim = new AlphaAnimation(0.3f, 1.0f);
            anim.setDuration(1000);
            anim.setRepeatCount(AlphaAnimation.INFINITE);
            anim.setRepeatMode(AlphaAnimation.REVERSE);
            dot.startAnimation(anim);
        } else {
            dot.clearAnimation();
            dot.setVisibility(View.GONE);
        }
    }
}
