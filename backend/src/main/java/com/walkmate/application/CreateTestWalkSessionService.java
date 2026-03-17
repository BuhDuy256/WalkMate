package com.walkmate.application;

import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.infrastructure.exception.ErrorCode;
import com.walkmate.domain.session.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateTestWalkSessionService {
    private final SessionRepository sessionRepository;

    @Transactional
    public WalkSession execute(
            UUID user1Id,
            UUID user2Id,
            Instant scheduledStartTime,
            Instant scheduledEndTime,
            boolean mutualConfirmation) {

        if (!mutualConfirmation) {
            throw new DomainException(
                    ErrorCode.SESSION_INVALID_TRANSITION,
                    "Test session creation requires mutualConfirmation=true to simulate confirmed proposal precondition.");
        }

        if (user1Id.equals(user2Id)) {
            throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "Participants must be distinct users.");
        }

        if (!scheduledEndTime.isAfter(scheduledStartTime)) {
            throw new DomainException(ErrorCode.SESSION_INVALID_TRANSITION, "Invalid session time window.");
        }

        if (sessionRepository.hasOverlappingPendingOrActive(user1Id, scheduledStartTime, scheduledEndTime)
                || sessionRepository.hasOverlappingPendingOrActive(user2Id, scheduledStartTime, scheduledEndTime)) {
            throw new DomainException(
                    ErrorCode.SESSION_CONFLICT,
                    "Overlapping PENDING/ACTIVE session exists for at least one participant.");
        }

        WalkSession session = new WalkSession(
                UUID.randomUUID(),
                user1Id,
                user2Id,
                scheduledStartTime,
                scheduledEndTime,
                null,
                null,
                null,
                null,
                SessionStatus.PENDING,
                BigDecimal.ZERO,
                0L,
                null,
                null,
                null,
                0L);

        return sessionRepository.save(session);
    }
}
