package com.walkmate.application.user;

public record RegisterUserCommand(
        String fullName,
        String email,
        String password
) {
}
