package com.walkmate.infrastructure.repository;

import com.walkmate.domain.session.SessionRepository;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure implementation of the Domain SessionRepository.
 * Follows DDD principle: Domain interfaces are implemented in Infrastructure layer.
 */
@Repository
@RequiredArgsConstructor
public class JpaSessionRepository implements SessionRepository {

    private final SpringDataSessionRepository springDataRepo;

    @Override
    public Optional<WalkSession> findById(UUID id) {
        return springDataRepo.findById(id);
    }

    @Override
    public void save(WalkSession session) {
        springDataRepo.save(session);
    }

    @Override
    public List<WalkSession> findByStatusAndScheduledStartTimeBefore(SessionStatus status, LocalDateTime time) {
        return springDataRepo.findByStatusAndScheduledStartTimeBefore(status, time);
    }

    @Override
    public boolean hasOverlappingSession(UUID userId, LocalDateTime start, LocalDateTime end, List<SessionStatus> statusList) {
        return springDataRepo.hasOverlappingSession(userId, start, end, statusList);
    }
}
