package com.walkmate.presentation.mapper;

import com.walkmate.domain.session.WalkSession;
import com.walkmate.presentation.dto.response.SessionResponse;
import org.springframework.stereotype.Component;

@Component
public class SessionMapper {
  public SessionResponse toResponse(WalkSession session) {
    return new SessionResponse(
        session.getSessionId(),
        session.getUser1Id(),
        session.getUser2Id(),
        session.getStatus(),
        session.getScheduledStartTime(),
        session.getScheduledEndTime(),
        session.getActualStartTime(),
        session.getActualEndTime(),
        session.getTotalDistance(),
        session.getTotalDuration(),
        session.getVersion());
  }
}
