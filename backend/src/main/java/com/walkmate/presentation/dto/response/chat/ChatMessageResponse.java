package com.walkmate.presentation.dto.response.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.walkmate.domain.chat.ChatMessage;

/**
 * Response DTO for a single chat message.
 *
 * JSON field names use snake_case to match the Android client's ChatMessageDto
 * @SerializedName annotations exactly. Do NOT rename these without updating the
 * Android DTO in parallel.
 */
public record ChatMessageResponse(
        @JsonProperty("message_id")  String messageId,
        @JsonProperty("session_id")  String sessionId,
        @JsonProperty("sender_id")   String senderId,
        @JsonProperty("sender_name") String senderName,
        @JsonProperty("content")     String content,
        @JsonProperty("timestamp")   long   timestamp
) {
    public static ChatMessageResponse from(ChatMessage domain) {
        return new ChatMessageResponse(
                domain.getMessageId(),
                domain.getSessionId(),
                domain.getSenderId(),
                domain.getSenderName(),
                domain.getContent(),
                domain.getSentAt().toEpochMilli()
        );
    }
}
