package com.walkmate.presentation.mapper.session;

import com.walkmate.domain.session.WalkSession;
import com.walkmate.presentation.dto.response.session.WalkSessionResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SessionMapper {

    public WalkSessionResponse toResponse(WalkSession session) {
        return new WalkSessionResponse(
                session.getSessionId(),
                session.getProposalId(),
                session.getUserIdA(),
                session.getUserIdB(),
                session.getMeetingPointLat(),
                session.getMeetingPointLng(),
                fmt(session.getScheduledStart()),
                fmt(session.getScheduledEnd()),
                session.getStatus().name(),
                fmt(session.getCreatedAt()),
                fmt(session.getStartedAt()),
                fmt(session.getEndedAt()),
                fmt(session.getUserAActivatedAt()),
                fmt(session.getUserBActivatedAt()),
                session.getCancellationReason(),
                session.getAbortReason() != null ? session.getAbortReason().name() : null
        );
    }

    private static String fmt(Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
