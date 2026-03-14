package com.walkmate.application;

public record RegisterUserCommand(
        String fullName,
        String email,
        String password
) {
}