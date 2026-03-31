package com.walkmate.presentation.controller.user;

import com.walkmate.application.user.LoginResult;
import com.walkmate.application.user.LoginUserCommand;
import com.walkmate.application.user.RegisterUserCommand;
import com.walkmate.application.user.UserCommandService;
import com.walkmate.domain.user.User;
import com.walkmate.presentation.dto.request.user.LoginUserRequest;
import com.walkmate.presentation.dto.request.user.RegisterUserRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.user.LoginUserResponse;
import com.walkmate.presentation.dto.response.user.RegisterUserResponse;
import com.walkmate.presentation.mapper.user.UserMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Register and login endpoints")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserCommandService userCommandService;
    private final UserMapper userMapper;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterUserResponse>> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        User user = userCommandService.registerUser(
                new RegisterUserCommand(request.fullname(), request.email(), request.password())
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userMapper.toRegisterResponse(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginUserResponse>> loginUser(@Valid @RequestBody LoginUserRequest request) {
        LoginResult loginResult = userCommandService.loginUser(
                new LoginUserCommand(request.email(), request.password())
        );
        return ResponseEntity.ok(ApiResponse.success(userMapper.toLoginUserResponse(loginResult)));
    }
}
