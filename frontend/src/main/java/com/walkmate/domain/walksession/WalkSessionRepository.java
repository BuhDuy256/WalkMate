package com.walkmate.domain.walksession;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface WalkSessionRepository {
    void getActiveSessions(DomainCallback<List<WalkSession>> callback);
    void cancelSession(String sessionId, String reason, DomainCallback<Void> callback);
    void activateSession(String sessionId, DomainCallback<WalkSession> callback);
}
