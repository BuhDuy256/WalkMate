package com.walkmate.domain.user;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("Email already exists: " + email);
    }
}