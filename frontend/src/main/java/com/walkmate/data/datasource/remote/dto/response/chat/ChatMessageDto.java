package com.walkmate.data.datasource.remote.dto.response.chat;

import com.google.gson.annotations.SerializedName;

// ============================================================================
// TODO: CRITICAL — Verify these @SerializedName keys against the final
// Backend API contract (Swagger / Postman) before release.
//
// Assumed WebSocket inbound message shape (server → client):
// {
//   "message_id":  "uuid-string",
//   "session_id":  "uuid-string",
//   "sender_id":   "uuid-string",
//   "sender_name": "Display Name" | null,
//   "content":     "message text",
//   "timestamp":   1712650000000   // epoch ms — long
// }
//
// If the backend uses different key names (e.g. "id" instead of "message_id",
// "createdAt" instead of "timestamp") update the @SerializedName values here.
// DO NOT change the Java field names — only the annotation values.
// ============================================================================
public class ChatMessageDto {

    @SerializedName("message_id")
    public String messageId;

    @SerializedName("session_id")
    public String sessionId;

    @SerializedName("sender_id")
    public String senderId;

    @SerializedName("sender_name")
    public String senderName;   // nullable

    @SerializedName("content")
    public String content;

    @SerializedName("timestamp")
    public long timestamp;      // epoch milliseconds
}
