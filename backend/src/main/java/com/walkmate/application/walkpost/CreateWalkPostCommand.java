package com.walkmate.application.walkpost;

public record CreateWalkPostCommand(
        String sessionId,
        String authorId,
        String caption,
        String visibility,
        boolean showCompanion,
        boolean showRouteMap,
        boolean showStats
) {}
