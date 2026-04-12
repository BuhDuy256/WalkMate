package com.walkmate.application.user;

public record LoginUserCommand(
        String email,
        String password,
        String deviceId
) {
}
