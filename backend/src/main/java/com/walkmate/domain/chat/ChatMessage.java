package com.walkmate.domain.chat;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity for a single chat message.
 * No MongoDB or framework annotations — pure Java.
 */
@Getter
public class ChatMessage {

    private final String  messageId;
    private final String  sessionId;
    private final String  senderId;
    private final String  senderName;  // denormalized snapshot at send time
    private final String  content;
    private final Instant sentAt;

    public ChatMessage(String messageId, String sessionId, String senderId,
                       String senderName, String content, Instant sentAt) {
        this.messageId  = messageId;
        this.sessionId  = sessionId;
        this.senderId   = senderId;
        this.senderName = senderName;
        this.content    = content;
        this.sentAt     = sentAt;
    }

    public static ChatMessage create(String sessionId, String senderId,
                                     String senderName, String content) {
        return new ChatMessage(
                UUID.randomUUID().toString(),
                sessionId, senderId, senderName, content,
                Instant.now()
        );
    }
}
