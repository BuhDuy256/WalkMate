package com.walkmate.domain.chatroom;

import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for the ChatRoom domain.
 *
 * One ChatRoom exists per WalkSession. Status transitions from OPEN to CLOSED
 * when the associated WalkSession reaches a terminal state (server-side).
 *
 * Pure Java — zero android.* or androidx.* imports.
 */
public class ChatRoom {

    private final String chatRoomId;
    private final String sessionId;
    private final String participantA;
    private final String participantB;
    private final ChatRoomStatus status;
    private final List<ChatMessage> messages;

    public ChatRoom(
            String chatRoomId,
            String sessionId,
            String participantA,
            String participantB,
            ChatRoomStatus status,
            List<ChatMessage> messages) {
        this.chatRoomId = chatRoomId;
        this.sessionId = sessionId;
        this.participantA = participantA;
        this.participantB = participantB;
        this.status = status;
        this.messages = messages != null
                ? Collections.unmodifiableList(messages)
                : Collections.emptyList();
    }

    public String getChatRoomId()        { return chatRoomId; }
    public String getSessionId()         { return sessionId; }
    public String getParticipantA()      { return participantA; }
    public String getParticipantB()      { return participantB; }
    public ChatRoomStatus getStatus()    { return status; }
    public List<ChatMessage> getMessages() { return messages; }

    /** Convenience: returns true when participants may still send messages. */
    public boolean isOpen() {
        return status == ChatRoomStatus.OPEN;
    }
}
