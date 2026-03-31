package com.walkmate.domain.session;

import java.util.List;
import java.util.Optional;

public interface WalkSessionRepository {
    WalkSession save(WalkSession session);
    Optional<WalkSession> findByProposalId(String proposalId);
    List<WalkSession> findActiveForUser(String userId);
}
