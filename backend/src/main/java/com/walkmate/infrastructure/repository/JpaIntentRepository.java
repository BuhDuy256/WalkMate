package com.walkmate.infrastructure.repository;

import com.walkmate.domain.intent.IntentRepository;
import com.walkmate.domain.intent.WalkIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure implementation of IntentRepository matching Invariant 2.
 */
@Repository
@RequiredArgsConstructor
public class JpaIntentRepository implements IntentRepository {

    private final SpringDataIntentRepository springDataRepo;

    @Override
    public void save(WalkIntent intent) {
        springDataRepo.save(intent);
    }

    @Override
    public Optional<WalkIntent> findById(UUID id) {
        return springDataRepo.findById(id);
    }

    @Override
    public boolean hasOverlappingOpenIntent(UUID userId, LocalDateTime newStart, LocalDateTime newEnd) {
        return springDataRepo.hasOverlappingOpenIntent(userId, newStart, newEnd);
    }
}
