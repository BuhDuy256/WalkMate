package com.walkmate.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.domain.chat.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_MINE   = 0;
    private static final int VIEW_TYPE_THEIRS = 1;

    private List<ChatMessage> messages = new ArrayList<>();

    public void submitList(List<ChatMessage> newMessages) {
        this.messages = newMessages != null ? newMessages : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isFromMe() ? VIEW_TYPE_MINE : VIEW_TYPE_THEIRS;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = (viewType == VIEW_TYPE_MINE)
                ? R.layout.item_chat_mine
                : R.layout.item_chat_theirs;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView txtContent;
        private final TextView txtTime;
        private final TextView txtSenderName; // may be null in VIEW_TYPE_MINE layout

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            txtContent    = itemView.findViewById(R.id.txtContent);
            txtTime       = itemView.findViewById(R.id.txtTime);
            txtSenderName = itemView.findViewById(R.id.txtSenderName); // null-safe
        }

        void bind(ChatMessage msg) {
            txtContent.setText(msg.getContent());
            txtTime.setText(formatTime(msg.getTimestampMs()));
            if (txtSenderName != null) {
                String name = msg.getSenderName();
                if (name != null && !name.isEmpty()) {
                    txtSenderName.setVisibility(View.VISIBLE);
                    txtSenderName.setText(name);
                } else {
                    txtSenderName.setVisibility(View.GONE);
                }
            }
        }

        private String formatTime(long timestampMs) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestampMs));
        }
    }
}
