package com.walkmate.application;

import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetWalkSessionService {
    private final SessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public WalkSession execute(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
    }
}
