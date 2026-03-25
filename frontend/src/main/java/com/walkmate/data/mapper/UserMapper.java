package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.request.user.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.RegisterRequestDto;
import com.walkmate.data.datasource.remote.dto.response.user.LoginResponseDto;
import com.walkmate.data.datasource.remote.dto.response.user.RegisterResponseDto;
import com.walkmate.domain.user.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static LoginRequestDto toLoginRequest(String email, String password) {
        return new LoginRequestDto(
                requireText(email, "Email is required"),
                requireText(password, "Password is required"));
    }

    public static RegisterRequestDto toRegisterRequest(String fullName, String email, String password) {
        return new RegisterRequestDto(
                requireText(fullName, "Full name is required"),
                requireText(email, "Email is required"),
                requireText(password, "Password is required"));
    }

    public static String toAccessToken(LoginResponseDto responseDto) {
        if (responseDto == null) {
            throw new IllegalArgumentException("Login response is empty");
        }
        return requireText(responseDto.getAccessToken(), "Access token is missing");
    }

    public static User toDomainUser(RegisterResponseDto responseDto) {
        if (responseDto == null) {
            throw new IllegalArgumentException("Register response is empty");
        }

        return new User(
                requireText(responseDto.getId(), "User id is missing"),
                requireText(responseDto.getFullname(), "Full name is missing"),
                requireText(responseDto.getEmail(), "Email is missing"));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
