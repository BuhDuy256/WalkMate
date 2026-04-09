package com.walkmate.application.user;

import java.util.UUID;

public record LogoutCommand(UUID userId, String deviceId) {
}
