package com.walkmate.presentation.controller.user;

import com.walkmate.application.user.GoogleAuthCommand;
import com.walkmate.application.user.LoginResult;
import com.walkmate.application.user.LoginUserCommand;
import com.walkmate.application.user.RegisterUserCommand;
import com.walkmate.application.user.SendOtpCommand;
import com.walkmate.application.user.UserCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.user.VerifyOtpCommand;
import com.walkmate.presentation.dto.request.user.GoogleLoginRequest;
import com.walkmate.presentation.dto.request.user.LoginUserRequest;
import com.walkmate.presentation.dto.request.user.LogoutRequest;
import com.walkmate.presentation.dto.request.user.RefreshTokenRequest;
import com.walkmate.presentation.dto.request.user.RegisterUserRequest;
import com.walkmate.presentation.dto.request.user.SendOtpRequest;
import com.walkmate.presentation.dto.request.user.VerifyOtpRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.user.LoginUserResponse;
import com.walkmate.presentation.mapper.user.UserMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Auth", description = "Register and login endpoints")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserCommandService userCommandService;
    private final UserMapper         userMapper;

    // ── Register / Login ──────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginUserResponse>> registerUser(
            @Valid @RequestBody RegisterUserRequest request) {

        LoginResult loginResult = userCommandService.registerUser(
                new RegisterUserCommand(request.fullname(), request.email(),
                        request.password(), request.deviceId()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userMapper.toLoginUserResponse(loginResult)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginUserResponse>> loginUser(
            @Valid @RequestBody LoginUserRequest request) {

        LoginResult loginResult = userCommandService.loginUser(
                new LoginUserCommand(request.email(), request.password(), request.deviceId()));

        return ResponseEntity.ok(ApiResponse.success(userMapper.toLoginUserResponse(loginResult)));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginUserResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {

        LoginResult loginResult = userCommandService.loginOrRegisterWithGoogle(
                new GoogleAuthCommand(request.idToken(), request.deviceId()));

        return ResponseEntity.ok(ApiResponse.success(userMapper.toLoginUserResponse(loginResult)));
    }

    // ── Token rotation ────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginUserResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        LoginResult loginResult = userCommandService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(userMapper.toLoginUserResponse(loginResult)));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LogoutRequest request) {

        UUID userId = UUID.fromString(principal.userId());
        userCommandService.logout(userId, request.deviceId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID userId = UUID.fromString(principal.userId());
        userCommandService.logoutAll(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Phone OTP ─────────────────────────────────────────────────────────────

    @PostMapping("/phone/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @Valid @RequestBody SendOtpRequest request) {

        userCommandService.sendOtp(new SendOtpCommand(request.phone()));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<ApiResponse<LoginUserResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        LoginResult loginResult = userCommandService.verifyOtp(
                new VerifyOtpCommand(request.phone(), request.code(), request.deviceId()));

        return ResponseEntity.ok(ApiResponse.success(userMapper.toLoginUserResponse(loginResult)));
    }
}
