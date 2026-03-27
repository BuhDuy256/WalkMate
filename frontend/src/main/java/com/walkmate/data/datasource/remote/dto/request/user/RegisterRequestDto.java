package com.walkmate.data.datasource.remote.dto.request.user;

public class RegisterRequestDto {
    private final String fullname;
    private final String email;
    private final String password;

    public RegisterRequestDto(String fullname, String email, String password) {
        this.fullname = fullname;
        this.email = email;
        this.password = password;
    }

    public String getFullname() {
        return fullname;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}
