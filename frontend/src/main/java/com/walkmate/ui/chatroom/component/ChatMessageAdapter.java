package com.walkmate.ui.chatroom.component;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.ui.chatroom.ChatroomUiState;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the chat message thread.
 *
 * Supports three view types:
 *   VIEW_TYPE_OUTGOING — messages sent by the current user (orange bubble, right-aligned)
 *   VIEW_TYPE_INCOMING — messages sent by the partner (white bubble, left-aligned)
 *   VIEW_TYPE_SYSTEM   — system / status messages (centered pill)
 *
 * submitList() is the ONLY method for pushing new data; it is called from renderState() only.
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_OUTGOING = 0;
    private static final int VIEW_TYPE_INCOMING = 1;
    private static final int VIEW_TYPE_SYSTEM   = 2;

    private List<ChatroomUiState.MessageSnapshot> items = new ArrayList<>();

    /** Called from renderState() only — no direct view mutations elsewhere. */
    public void submitList(List<ChatroomUiState.MessageSnapshot> newItems) {
        this.items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        notifyDataSetChanged();
    }

    // ── Adapter overrides ─────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        ChatroomUiState.MessageSnapshot snap = items.get(position);
        if (snap.isSystem)  return VIEW_TYPE_SYSTEM;
        if (snap.isOwn)     return VIEW_TYPE_OUTGOING;
        return VIEW_TYPE_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_OUTGOING:
                return new OutgoingViewHolder(
                        inflater.inflate(R.layout.item_chat_message_outgoing, parent, false));
            case VIEW_TYPE_SYSTEM:
                return new SystemViewHolder(
                        inflater.inflate(R.layout.item_chat_message_system, parent, false));
            default: // VIEW_TYPE_INCOMING
                return new IncomingViewHolder(
                        inflater.inflate(R.layout.item_chat_message_incoming, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatroomUiState.MessageSnapshot snap = items.get(position);
        switch (getItemViewType(position)) {
            case VIEW_TYPE_OUTGOING:
                ((OutgoingViewHolder) holder).bind(snap);
                break;
            case VIEW_TYPE_SYSTEM:
                ((SystemViewHolder) holder).bind(snap);
                break;
            default:
                ((IncomingViewHolder) holder).bind(snap);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ── ViewHolder: Outgoing ──────────────────────────────────────────────────

    static class OutgoingViewHolder extends RecyclerView.ViewHolder {

        private final TextView txtContent;
        private final TextView txtTime;

        OutgoingViewHolder(@NonNull View itemView) {
            super(itemView);
            txtContent = itemView.findViewById(R.id.txtContent);
            txtTime    = itemView.findViewById(R.id.txtTime);
        }

        void bind(ChatroomUiState.MessageSnapshot snap) {
            txtContent.setText(snap.content);
            txtTime.setText(snap.formattedTime);
        }
    }

    // ── ViewHolder: Incoming ──────────────────────────────────────────────────

    static class IncomingViewHolder extends RecyclerView.ViewHolder {

        private final AvatarInitialView avatarSender;
        private final TextView txtSenderName;
        private final TextView txtContent;
        private final TextView txtTime;

        IncomingViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarSender  = itemView.findViewById(R.id.avatarSender);
            txtSenderName = itemView.findViewById(R.id.txtSenderName);
            txtContent    = itemView.findViewById(R.id.txtContent);
            txtTime       = itemView.findViewById(R.id.txtTime);
        }

        void bind(ChatroomUiState.MessageSnapshot snap) {
            avatarSender.bind(snap.senderName, snap.senderAvatarUrl);
            txtSenderName.setText(snap.senderName);
            txtContent.setText(snap.content);
            txtTime.setText(snap.formattedTime);
        }
    }

    // ── ViewHolder: System ────────────────────────────────────────────────────

    static class SystemViewHolder extends RecyclerView.ViewHolder {

        private final TextView txtSystem;

        SystemViewHolder(@NonNull View itemView) {
            super(itemView);
            txtSystem = itemView.findViewById(R.id.txtSystem);
        }

        void bind(ChatroomUiState.MessageSnapshot snap) {
            txtSystem.setText(snap.content);
        }
    }
}
