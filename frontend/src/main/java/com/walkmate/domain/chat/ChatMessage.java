package com.walkmate.domain.chat;

// ============================================================================
// TODO: CRITICAL - Verify these @SerializedName keys against the final
// Backend API contract (Swagger / Postman) before release.
//
// The JSON field names this class is mapped from are defined in:
//   data/datasource/remote/dto/response/chat/ChatMessageDto.java
//
// If the backend changes any key name, update @SerializedName there — NOT here.
// ============================================================================
public class ChatMessage {
    private final String messageId;
    private final String sessionId;
    private final String senderId;
    private final String senderName;  // nullable, resolved via profile cache
    private final String content;
    private final long timestampMs;
    private final boolean isFromMe;   // computed: senderId.equals(currentUserId)

    public ChatMessage(String messageId, String sessionId, String senderId,
                       String senderName, String content, long timestampMs,
                       boolean isFromMe) {
        this.messageId   = messageId;
        this.sessionId   = sessionId;
        this.senderId    = senderId;
        this.senderName  = senderName;
        this.content     = content;
        this.timestampMs = timestampMs;
        this.isFromMe    = isFromMe;
    }

    public String getMessageId()   { return messageId; }
    public String getSessionId()   { return sessionId; }
    public String getSenderId()    { return senderId; }
    public String getSenderName()  { return senderName; }
    public String getContent()     { return content; }
    public long   getTimestampMs() { return timestampMs; }
    public boolean isFromMe()      { return isFromMe; }
}
