package com.walkmate.presentation.mapper.user;

import com.walkmate.application.user.LoginResult;
import com.walkmate.domain.user.User;
import com.walkmate.presentation.dto.response.user.LoginUserResponse;
import com.walkmate.presentation.dto.response.user.RegisterUserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public RegisterUserResponse toRegisterResponse(User user) {
        return new RegisterUserResponse(user.getEmail());
    }

    public LoginUserResponse toLoginUserResponse(LoginResult loginResult) {
        return new LoginUserResponse(loginResult.accessToken(), "Bearer", loginResult.expiresIn());
    }
}
