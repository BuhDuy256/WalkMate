package com.walkmate.application;

import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.WalkSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Application Service orchestrating Use Cases for WalkSession.
 * Enforces transactional boundaries and calls domain behaviors.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository; // Note: You'll need an implementation of this interface annotated with @Repository

    @Transactional
    public void activateSession(UUID sessionId, UUID userId) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        session.activate(userId, LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void completeSession(UUID sessionId, UUID userId) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        session.complete(userId, LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void cancelSession(UUID sessionId, UUID userId, String reason) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        session.cancel(userId, reason, LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void reportNoShow(UUID sessionId, UUID reportingUserId, UUID absentUserId) {
        WalkSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        
        session.reportNoShow(reportingUserId, absentUserId, LocalDateTime.now());
        sessionRepository.save(session);
    }
}
