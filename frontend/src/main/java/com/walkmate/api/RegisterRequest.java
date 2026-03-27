package com.walkmate.api;

public class RegisterRequest {
    private String fullname;
    private String email;
    private String password;

    public RegisterRequest(String fullname, String email, String password) {
        this.fullname = fullname;
        this.email = email;
        this.password = password;
    }

    // Getters and Setters
}