package com.example.mainapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_bubble, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messageList.get(position);
        boolean isSent = message.getSenderId().equals(currentUserId);

        // Hide both first
        holder.sentLayout.setVisibility(View.GONE);
        holder.receivedLayout.setVisibility(View.GONE);

        if (isSent) {
            holder.sentLayout.setVisibility(View.VISIBLE);

            // Text vs image
            if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                holder.tvSentMessage.setVisibility(View.GONE);
                holder.imgSentMessage.setVisibility(View.VISIBLE);

                Glide.with(holder.itemView.getContext())
                        .load(message.getImageUrl())
                        .into(holder.imgSentMessage);
            } else {
                holder.tvSentMessage.setVisibility(View.VISIBLE);
                holder.imgSentMessage.setVisibility(View.GONE);
                holder.tvSentMessage.setText(message.getMessage());
            }

            // Timestamp
            holder.tvSentTime.setText(formatTime(message.getTimestamp()));

            // Read receipts
            if (message.isSeen()) {
                holder.tvReadReceipt.setText("✓✓");
                holder.tvReadReceipt.setTextColor(0xFF2196F3); // blue
            } else {
                holder.tvReadReceipt.setText("✓");
                holder.tvReadReceipt.setTextColor(0xFF757575); // gray
            }

        } else {
            holder.receivedLayout.setVisibility(View.VISIBLE);

            if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                holder.tvReceivedMessage.setVisibility(View.GONE);
                holder.imgReceivedMessage.setVisibility(View.VISIBLE);

                Glide.with(holder.itemView.getContext())
                        .load(message.getImageUrl())
                        .into(holder.imgReceivedMessage);
            } else {
                holder.tvReceivedMessage.setVisibility(View.VISIBLE);
                holder.imgReceivedMessage.setVisibility(View.GONE);
                holder.tvReceivedMessage.setText(message.getMessage());
            }

            holder.tvReceivedTime.setText(formatTime(message.getTimestamp()));
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout sentLayout, receivedLayout;
        TextView tvSentMessage, tvSentTime, tvReadReceipt;
        TextView tvReceivedMessage, tvReceivedTime;
        ImageView imgSentMessage, imgReceivedMessage;

        MessageViewHolder(View itemView) {
            super(itemView);
            sentLayout = itemView.findViewById(R.id.sentLayout);
            receivedLayout = itemView.findViewById(R.id.receivedLayout);

            tvSentMessage = itemView.findViewById(R.id.tvSentMessage);
            tvSentTime = itemView.findViewById(R.id.tvSentTime);
            tvReadReceipt = itemView.findViewById(R.id.tvReadReceipt);
            imgSentMessage = itemView.findViewById(R.id.imgSentMessage);

            tvReceivedMessage = itemView.findViewById(R.id.tvReceivedMessage);
            tvReceivedTime = itemView.findViewById(R.id.tvReceivedTime);
            imgReceivedMessage = itemView.findViewById(R.id.imgReceivedMessage);
        }
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "--:--";
        return new SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(new Date(timestamp));
    }
}
