package com.example.mainapplication;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final ArrayList<Message> messageList;
    private final String currentUserId;

    public MessageAdapter(ArrayList<Message> messageList) {
        this.messageList = messageList;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == 1)
                ? R.layout.item_message_sent
                : R.layout.item_message_received;

        View view = LayoutInflater.from(parent.getContext())
                .inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messageList.get(position);
        if (message == null) return;

        // --- Message text ---
        String text = message.getMessage();
        if (text == null || text.trim().isEmpty()) {
            text = "[Message unavailable]";
        }
        holder.messageText.setText(message.getMessage());

        // --- Timestamp ---
        long ts = message.getTimestamp();
        if (ts > 0) {
            String time = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                    .format(new Date(ts));
            holder.timestamp.setText(time);
        }else {
            holder.timestamp.setText("--:--");
        }

        // --- Read receipts (for sent messages only) ---
        if (message.getSenderId() != null && message.getSenderId().equals(currentUserId)) {
            holder.readReceipt.setVisibility(View.VISIBLE);

            if (message.isSeen()) {
                holder.readReceipt.setText("✓✓");
                holder.readReceipt.setTextColor(0xFF2196F3);
            } else {
                holder.readReceipt.setText("✓");
                holder.readReceipt.setTextColor(0xFF757575);
            }
        } else {
            holder.readReceipt.setVisibility(View.GONE);
        }

        holder.itemView.setAlpha(0f);
        holder.itemView.animate().alpha(1f).setDuration(150).start();
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).getSenderId().equals(currentUserId) ? 1 : 0;
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timestamp, readReceipt;

        MessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.tvMessage);
            timestamp = itemView.findViewById(R.id.tvTimestamp);
            readReceipt = itemView.findViewById(R.id.tvReadReceipt);
        }
    }
}
