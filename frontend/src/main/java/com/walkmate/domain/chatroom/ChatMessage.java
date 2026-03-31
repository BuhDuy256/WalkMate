package com.walkmate.domain.chatroom;

/**
 * Immutable value object representing a single message in a ChatRoom.
 *
 * Pure Java — zero android.* or androidx.* imports.
 */
public class ChatMessage {

    private final String messageId;
    private final String senderId;
    private final String content;
    private final long timestampMs;

    public ChatMessage(String messageId, String senderId, String content, long timestampMs) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.content = content;
        this.timestampMs = timestampMs;
    }

    public String getMessageId()  { return messageId; }
    public String getSenderId()   { return senderId; }
    public String getContent()    { return content; }
    public long getTimestampMs()  { return timestampMs; }
}
