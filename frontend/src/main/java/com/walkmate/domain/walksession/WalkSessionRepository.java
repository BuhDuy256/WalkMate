package com.walkmate.domain.walksession;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface WalkSessionRepository {
    void getActiveSessions(DomainCallback<List<WalkSession>> callback);
    void activateSession(String sessionId, DomainCallback<WalkSession> callback);
    void cancelSession(String sessionId, String reason, DomainCallback<Void> callback);
    void completeSession(String sessionId, DomainCallback<WalkSession> callback);
    void getSessionHistory(DomainCallback<List<SessionSummary>> callback);
    void getSessionSummary(String sessionId, DomainCallback<SessionSummary> callback);
    void getSessionRoute(String sessionId, DomainCallback<SessionRoute> callback);
    void reportSession(String sessionId, String reportedUserId,
                       String reason, String evidenceUrl,
                       DomainCallback<Void> callback);

    void fetchQrToken(String sessionId, DomainCallback<String> callback);

    void verifyPartnerQr(String sessionId, String partnerQrToken, DomainCallback<Void> callback);
}
