package com.walkmate.presentation.mapper;

import com.walkmate.application.LoginResult;
import com.walkmate.domain.user.User;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.LoginResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public ApiResponse toRegisterResponse(User user) {
        return ApiResponse.success("Register successful");
    }

    public LoginResponse toLoginResponse(LoginResult loginResult) {
        return new LoginResponse(loginResult.accessToken(), "Bearer", loginResult.expiresIn());
    }
}