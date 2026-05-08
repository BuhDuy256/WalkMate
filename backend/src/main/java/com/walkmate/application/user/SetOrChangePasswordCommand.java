package com.walkmate.application.user;

import java.util.UUID;

public record SetOrChangePasswordCommand(
        UUID userId,
        String currentPassword,
        String newPassword
) {}
