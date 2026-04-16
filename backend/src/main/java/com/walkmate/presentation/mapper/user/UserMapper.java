package com.walkmate.presentation.mapper.user;

import com.walkmate.application.user.LoginResult;
import com.walkmate.presentation.dto.response.user.LoginUserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public LoginUserResponse toLoginUserResponse(LoginResult loginResult) {
        return new LoginUserResponse(
                loginResult.accessToken(),
                "Bearer",
                loginResult.expiresIn(),
                loginResult.refreshToken(),
                loginResult.refreshTokenExpiresIn());
    }
}
