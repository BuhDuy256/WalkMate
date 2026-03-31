package com.walkmate.ui.chatroom;

import java.util.List;

/**
 * Immutable snapshot of all data rendered on the Chatroom screen.
 *
 * Rule: no setters. ViewModel calls postValue(new ChatroomUiState(...)) for every
 * state change. Inner snapshot classes use public final fields for brevity (intentional).
 */
public class ChatroomUiState {

    // ── Inner snapshots ───────────────────────────────────────────────────────

    public static class PartnerSnapshot {
        public final String partnerId;
        public final String partnerName;
        public final String partnerAvatarUrl;  // null → show placeholder
        public final boolean isOnline;

        public PartnerSnapshot(String partnerId, String partnerName,
                               String partnerAvatarUrl, boolean isOnline) {
            this.partnerId = partnerId;
            this.partnerName = partnerName;
            this.partnerAvatarUrl = partnerAvatarUrl;
            this.isOnline = isOnline;
        }
    }

    public static class MessageSnapshot {
        public final String messageId;
        public final String senderId;
        public final String senderName;
        public final String senderAvatarUrl;  // null → show placeholder
        public final String content;
        public final String formattedTime;    // e.g. "14:32"
        public final boolean isOwn;           // sent by current user
        public final boolean isSystem;        // system / status message

        public MessageSnapshot(String messageId, String senderId,
                               String senderName, String senderAvatarUrl,
                               String content, String formattedTime,
                               boolean isOwn, boolean isSystem) {
            this.messageId = messageId;
            this.senderId = senderId;
            this.senderName = senderName;
            this.senderAvatarUrl = senderAvatarUrl;
            this.content = content;
            this.formattedTime = formattedTime;
            this.isOwn = isOwn;
            this.isSystem = isSystem;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final boolean isLoading;
    private final boolean isChatOpen;          // false = CLOSED room → input hidden
    private final boolean showMatchBanner;     // true when session is PENDING_MEET
    private final long countdownEndEpochMs;    // 0 = no countdown
    private final PartnerSnapshot partner;
    private final List<MessageSnapshot> messages;
    private final String error;                // non-null for one-time toast errors

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChatroomUiState(
            boolean isLoading,
            boolean isChatOpen,
            boolean showMatchBanner,
            long countdownEndEpochMs,
            PartnerSnapshot partner,
            List<MessageSnapshot> messages,
            String error) {
        this.isLoading = isLoading;
        this.isChatOpen = isChatOpen;
        this.showMatchBanner = showMatchBanner;
        this.countdownEndEpochMs = countdownEndEpochMs;
        this.partner = partner;
        this.messages = messages;
        this.error = error;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static ChatroomUiState loading() {
        return new ChatroomUiState(true, true, false, 0L, null, null, null);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isLoading()               { return isLoading; }
    public boolean isChatOpen()              { return isChatOpen; }
    public boolean isShowMatchBanner()       { return showMatchBanner; }
    public long getCountdownEndEpochMs()     { return countdownEndEpochMs; }
    public PartnerSnapshot getPartner()      { return partner; }
    public List<MessageSnapshot> getMessages() { return messages; }
    public String getError()                 { return error; }
}
