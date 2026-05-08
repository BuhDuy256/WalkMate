package com.walkmate.application.walkpost;

public record UpdateWalkPostVisibilityCommand(
        String postId,
        String requesterId,
        String newVisibility
) {}
