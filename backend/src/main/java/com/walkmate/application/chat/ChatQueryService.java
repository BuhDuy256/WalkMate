package com.walkmate.application.chat;

import com.walkmate.domain.chat.ChatErrorCode;
import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.chat.ChatMessageRepository;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application-layer service for reading chat history.
 *
 * Verifies the caller is a participant before returning messages, so the
 * endpoint cannot be used to read conversations the caller is not part of.
 */
@Service
@RequiredArgsConstructor
public class ChatQueryService {

    private final WalkSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * Returns the most recent {@code limit} messages for the given session,
     * ordered oldest-first (ready for chronological display).
     *
     * @throws DomainException SESSION_NOT_FOUND  if the session does not exist
     * @throws DomainException CHAT_UNAUTHORIZED  if requesterId is not a participant
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> findRecentMessages(String sessionId, String requesterId, int limit) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

        boolean isParticipant = requesterId.equals(session.getUserIdA())
                             || requesterId.equals(session.getUserIdB());
        if (!isParticipant) {
            throw new DomainException(ChatErrorCode.CHAT_UNAUTHORIZED);
        }

        int safeLimit = Math.min(limit, 100); // guard against unbounded requests
        return messageRepository.findLatestBySessionId(sessionId, safeLimit);
    }
}
