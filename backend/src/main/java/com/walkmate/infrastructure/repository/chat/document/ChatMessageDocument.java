package com.walkmate.infrastructure.repository.chat.document;

import com.walkmate.domain.chat.ChatMessage;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for a single chat message.
 *
 * Architecture notes:
 * - @Document and MongoDB-specific types are confined to infrastructure. Nothing
 *   from this class leaks into domain or application layers.
 * - The compound index {sessionId:1, sentAt:-1} covers the primary read pattern:
 *   "fetch latest N messages for a session", without a full collection scan.
 * - senderName is denormalized here (snapshot at send time) to avoid a JOIN-like
 *   lookup on every history fetch.
 */
@Document(collection = "chat_messages")
@CompoundIndex(def = "{'sessionId': 1, 'sentAt': -1}")
@Getter
public class ChatMessageDocument {

    @Id
    private String  messageId;
    private String  sessionId;
    private String  senderId;
    private String  senderName;
    private String  content;
    private Instant sentAt;

    public static ChatMessageDocument from(ChatMessage domain) {
        ChatMessageDocument doc = new ChatMessageDocument();
        doc.messageId  = domain.getMessageId();
        doc.sessionId  = domain.getSessionId();
        doc.senderId   = domain.getSenderId();
        doc.senderName = domain.getSenderName();
        doc.content    = domain.getContent();
        doc.sentAt     = domain.getSentAt();
        return doc;
    }
}
