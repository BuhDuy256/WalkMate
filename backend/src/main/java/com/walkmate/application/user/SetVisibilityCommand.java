package com.walkmate.application.user;

import com.walkmate.domain.user.VisibilityMode;

import java.util.UUID;

public record SetVisibilityCommand(UUID userId, VisibilityMode mode) {
}
