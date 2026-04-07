package com.walkmate.infrastructure.repository.chat.document;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document representing a chat room scoped to a WalkSession.
 *
 * Architecture notes:
 * - The @Document annotation and all MongoDB-specific types are confined to the
 *   infrastructure layer. Nothing from this class leaks into domain or application layers.
 * - ChatRoom.status ("OPEN"/"CLOSED") is a convenience field for queries only.
 *   It is NOT the S-7 write-gate. The application enforces S-7 by checking
 *   WalkSession.status in PostgreSQL before allowing chat writes.
 */
@Document(collection = "chat_rooms")
@Getter
public class ChatRoomDocument {

    @Id
    private String sessionId;        // UUID string — PK is the WalkSession ID

    private String status;           // "OPEN" or "CLOSED" (convenience cache, NOT the S-7 gatekeeper)
    private Instant createdAt;
    private Instant closedAt;        // null while OPEN

    public static ChatRoomDocument open(String sessionId) {
        ChatRoomDocument doc = new ChatRoomDocument();
        doc.sessionId  = sessionId;
        doc.status     = "OPEN";
        doc.createdAt  = Instant.now();
        doc.closedAt   = null;
        return doc;
    }

    public void close() {
        this.status   = "CLOSED";
        this.closedAt = Instant.now();
    }
}
