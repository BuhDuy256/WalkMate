package com.walkmate.presentation.controller;

import com.walkmate.application.RegisterUserCommand;
import com.walkmate.application.RegisterUserUseCase;
import com.walkmate.domain.user.User;
import com.walkmate.presentation.dto.request.RegisterUserRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.mapper.AuthMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final AuthMapper authMapper;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        User user = registerUserUseCase.execute(
                new RegisterUserCommand(request.fullname(), request.email(), request.password())
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authMapper.toRegisterResponse(user));
    }
}