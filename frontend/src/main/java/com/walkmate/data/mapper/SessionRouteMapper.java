package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.session.SessionRouteResponse;
import com.walkmate.domain.walksession.SessionRoute; // TODO Phase 4: create this domain model

/**
 * Maps {@link SessionRouteResponse} → {@link SessionRoute} (domain model created in Phase 4).
 *
 * <p>Used for UC-23 route display. Simple field copy — no computation needed.</p>
 */
public class SessionRouteMapper {

    public static SessionRoute toDomain(SessionRouteResponse response) {
        return new SessionRoute(
                response.getUserAPolylines(),
                response.getUserBPolylines(),
                response.getTotalDistanceKm(),
                response.getDurationMinutes()
        );
    }

    private SessionRouteMapper() {}
}
