package com.walkmate.domain.session;

import com.walkmate.domain.valueobject.SessionPoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
  Optional<WalkSession> findById(UUID sessionId);

  Optional<WalkSession> findByIdForUpdate(UUID sessionId);

  WalkSession save(WalkSession session);

  List<WalkSession> findExpiredPendingSessionsForUpdate(int limit);

  List<WalkSession> findExpiredActiveSessionsForUpdate(int limit);

  int appendSessionPoints(UUID sessionId, List<SessionPoint> points);
}
