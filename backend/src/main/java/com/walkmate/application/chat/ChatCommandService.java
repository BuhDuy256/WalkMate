package com.walkmate.application.chat;

import com.walkmate.application.user.UserQueryService;
import com.walkmate.domain.chat.ChatErrorCode;
import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.chat.ChatMessageRepository;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application-layer coordinator for inbound WebSocket chat messages.
 *
 * Responsibilities:
 *  1. validateParticipant  — auth check called once on WebSocket connect.
 *  2. getSenderName        — resolves display name; cached in WS session attrs.
 *  3. processMessage       — re-validates room open, persists, returns saved entity.
 *
 * No @Transactional: MongoDB writes are non-transactional at the Spring level
 * (consistent with MongoChatRoomRepository pattern). PostgreSQL reads are
 * read-only point lookups that do not need a managed transaction here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCommandService {

    private final WalkSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserQueryService      userQueryService;

    // ── Validation (called once on WebSocket connect) ─────────────────────────

    /**
     * Verifies that {@code userId} is a participant in {@code sessionId} and that
     * the session is still open (not COMPLETED or CANCELLED).
     *
     * @throws DomainException SESSION_NOT_FOUND if session does not exist
     * @throws DomainException CHAT_UNAUTHORIZED  if user is not a participant
     * @throws DomainException CHAT_ROOM_CLOSED   if session is already terminal
     */
    public void validateParticipant(String sessionId, String userId) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        boolean isParticipant = userId.equals(session.getUserIdA())
                             || userId.equals(session.getUserIdB());
        if (!isParticipant) {
            log.warn("Chat rejected — not a participant: sessionId={} userId={}", sessionId, userId);
            throw new DomainException(ChatErrorCode.CHAT_UNAUTHORIZED);
        }

        if (isTerminal(session)) {
            log.warn("Chat rejected — session terminal: sessionId={} status={}", sessionId, session.getStatus());
            throw new DomainException(ChatErrorCode.CHAT_ROOM_CLOSED);
        }
    }

    // ── Sender name (cached in WS session attributes after connect) ───────────

    /**
     * Returns the display name for {@code userId}.
     * Falls back to {@code "User"} if no profile row exists.
     */
    public String getSenderName(String userId) {
        String name = userQueryService.getDisplayName(UUID.fromString(userId));
        return name != null ? name : "User";
    }

    // ── Message processing ────────────────────────────────────────────────────

    /**
     * Validates the session is still open, creates a new {@link ChatMessage},
     * persists it to MongoDB, and returns the saved entity for broadcasting.
     *
     * @throws DomainException SESSION_NOT_FOUND if session disappeared
     * @throws DomainException CHAT_ROOM_CLOSED   if session became terminal mid-walk
     */
    public ChatMessage processMessage(String sessionId, String senderId,
                                      String senderName, String content) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        if (isTerminal(session)) {
            throw new DomainException(ChatErrorCode.CHAT_ROOM_CLOSED);
        }

        ChatMessage message = ChatMessage.create(sessionId, senderId, senderName, content);
        return messageRepository.save(message);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isTerminal(WalkSession session) {
        return session.getStatus() == SessionStatus.COMPLETED
            || session.getStatus() == SessionStatus.CANCELLED;
    }
}
