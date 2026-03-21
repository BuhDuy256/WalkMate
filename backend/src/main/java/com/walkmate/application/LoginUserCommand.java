package com.walkmate.application;

public record LoginUserCommand(
        String email,
        String password
) {
}
