package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.WalkMateApplication;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.AuthApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.UserApiService;
import com.walkmate.data.datasource.remote.dto.request.user.GoogleLoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.LogoutRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.RegisterRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.SetVisibilityRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.UpdateFcmTokenRequestDto;
import com.walkmate.data.datasource.remote.dto.response.user.SetVisibilityResponseDto;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.LoginResponseDto;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.user.VisibilityMode;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class UserRepositoryImpl implements UserRepository {

    private static final String TAG = "UserRepositoryImpl";

    private final SessionManager       sessionManager;
    private final AuthApiService       authApiService;
    private final UserApiService       userApiService;
    private final WalkMateApplication  application;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public UserRepositoryImpl(Context context) {
        this.application    = (WalkMateApplication) context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
        this.authApiService = ApiClient.getAuthApiService();
        this.userApiService = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(UserApiService.class);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Override
    public void login(String email, String password, String deviceId, DomainCallback<String> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<LoginResponseDto>> resp = authApiService
                        .login(new LoginRequestDto(email, password, deviceId))
                        .execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    LoginResponseDto data = resp.body().getData();
                    sessionManager.saveAccessToken(data.getAccessToken());
                    sessionManager.saveRefreshToken(data.getRefreshToken());
                    syncFcmToken();
                    Log.d(TAG, "login succeeded");
                    callback.onSuccess(data.getAccessToken());
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "USER_INVALID_CREDENTIALS");
                    Log.w(TAG, "login failed: " + apiError.getCode());
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "login network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void register(String fullname, String email, String password, String deviceId,
                         DomainCallback<String> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<LoginResponseDto>> resp = authApiService
                        .register(new RegisterRequestDto(fullname, email, password, deviceId))
                        .execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    LoginResponseDto data = resp.body().getData();
                    sessionManager.saveAccessToken(data.getAccessToken());
                    sessionManager.saveRefreshToken(data.getRefreshToken());
                    syncFcmToken();
                    Log.d(TAG, "register succeeded — auto-login");
                    callback.onSuccess(data.getAccessToken());
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "REGISTER_FAILED");
                    Log.w(TAG, "register failed: " + apiError.getCode());
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "register network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void loginWithGoogle(String googleIdToken, String deviceId, DomainCallback<String> callback) {
        // Step 1: Sign in to Firebase with the Google ID token to obtain a Firebase ID token.
        AuthCredential credential = GoogleAuthProvider.getCredential(googleIdToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    // Step 2: Retrieve the Firebase ID token.
                    authResult.getUser().getIdToken(false)
                            .addOnSuccessListener(tokenResult -> {
                                String firebaseIdToken = tokenResult.getToken();
                                // Step 3: Exchange Firebase ID token for WalkMate JWT on a background thread.
                                executor.execute(() -> {
                                    try {
                                        Response<ApiResponse<LoginResponseDto>> resp = authApiService
                                                .loginWithGoogle(new GoogleLoginRequestDto(firebaseIdToken, deviceId))
                                                .execute();

                                        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                                            LoginResponseDto data = resp.body().getData();
                                            sessionManager.saveAccessToken(data.getAccessToken());
                                            sessionManager.saveRefreshToken(data.getRefreshToken());
                                            syncFcmToken();
                                            Log.d(TAG, "Google login succeeded");
                                            callback.onSuccess(data.getAccessToken());
                                        } else {
                                            // Bug fix: use ErrorParser.extractApiError(resp) which reads
                                            // errorBody() — for non-2xx responses Retrofit sets body()
                                            // to null, so the old extractErrorCode(resp.body()) always
                                            // returned the fallback code "GOOGLE_LOGIN_FAILED".
                                            ApiError apiError = ErrorParser.extractApiError(resp, "GOOGLE_LOGIN_FAILED");
                                            Log.w(TAG, "Google backend login failed: " + apiError.getCode());
                                            callback.onError(new Exception(apiError.getCode()));
                                        }
                                    } catch (IOException e) {
                                        Log.e(TAG, "Google login network error", e);
                                        callback.onError(e);
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to get Firebase ID token", e);
                                callback.onError(new Exception("GOOGLE_LOGIN_FAILED"));
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase signInWithCredential failed", e);
                    callback.onError(new Exception("GOOGLE_LOGIN_FAILED"));
                });
    }



    // ── Session management ────────────────────────────────────────────────────

    @Override
    public void logout(DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                String deviceId = sessionManager.getOrGenerateDeviceId();
                Response<ApiResponse<Void>> resp = authApiService
                        .logout(new LogoutRequestDto(deviceId))
                        .execute();

                // Clear GPS tracking caches BEFORE wiping the token so the
                // userId can still be read from the JWT during cleanup.
                String userId = sessionManager.getUserId();
                application.clearTrackingCacheForUser(userId);

                // Clear the local session regardless of backend response.
                sessionManager.clearSession();

                if (resp.isSuccessful()) {
                    Log.d(TAG, "logout succeeded");
                    callback.onSuccess(null);
                } else {
                    String errorCode = extractErrorCode(resp.body(), "LOGOUT_FAILED");
                    Log.w(TAG, "logout backend error: " + errorCode);
                    // Session is already cleared locally — treat as success from the user's perspective.
                    callback.onSuccess(null);
                }
            } catch (IOException e) {
                Log.e(TAG, "logout network error", e);
                String userId = sessionManager.getUserId();
                application.clearTrackingCacheForUser(userId);
                sessionManager.clearSession();
                callback.onSuccess(null); // Local logout always succeeds.
            }
        });
    }

    @Override
    public void logoutAll(DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = authApiService.logoutAll().execute();

                String userId = sessionManager.getUserId();
                application.clearTrackingCacheForUser(userId);

                sessionManager.clearSession();

                if (resp.isSuccessful()) {
                    Log.d(TAG, "logoutAll succeeded");
                    callback.onSuccess(null);
                } else {
                    String errorCode = extractErrorCode(resp.body(), "LOGOUT_ALL_FAILED");
                    Log.w(TAG, "logoutAll backend error: " + errorCode);
                    callback.onSuccess(null); // Local session cleared — treat as success.
                }
            } catch (IOException e) {
                Log.e(TAG, "logoutAll network error", e);
                sessionManager.clearSession();
                callback.onSuccess(null);
            }
        });
    }



    // ── User settings ─────────────────────────────────────────────────────────

    @Override
    public void setVisibility(VisibilityMode mode, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<SetVisibilityResponseDto>> resp = userApiService
                        .setVisibility(new SetVisibilityRequestDto(mode.name()))
                        .execute();

                if (resp.isSuccessful()) {
                    Log.d(TAG, "visibility set to " + mode.name());
                    callback.onSuccess(null);
                } else {
                    String errorCode = extractErrorCode(resp.body(), "SET_VISIBILITY_FAILED");
                    Log.w(TAG, "setVisibility failed: " + errorCode);
                    callback.onError(new Exception(errorCode));
                }
            } catch (IOException e) {
                Log.e(TAG, "setVisibility network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Token / device helpers ────────────────────────────────────────────────

    @Override
    public void saveAccessToken(String token) {
        sessionManager.saveAccessToken(token);
    }

    @Override
    public String getAccessToken() {
        return sessionManager.getAccessToken();
    }

    @Override
    public String getOrGenerateDeviceId() {
        return sessionManager.getOrGenerateDeviceId();
    }

    // ── FCM ───────────────────────────────────────────────────────────────────

    @Override
    public void syncFcmToken() {
        if (!sessionManager.hasUsableAccessToken()) return;
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.isBlank()) return;
                    updateFcmToken(token, new DomainCallback<Void>() {
                        @Override public void onSuccess(Void result) {}
                        @Override public void onError(Exception error) {
                            Log.w(TAG, "syncFcmToken failed: " + error.getMessage());
                        }
                    });
                })
                .addOnFailureListener(e ->
                        Log.w(TAG, "syncFcmToken: failed to retrieve token: " + e.getMessage()));
    }

    @Override
    public void updateFcmToken(String token, DomainCallback<Void> callback) {
        if (!sessionManager.hasUsableAccessToken()) {
            Log.d(TAG, "updateFcmToken skipped — user not authenticated");
            return;
        }
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = userApiService
                        .updateFcmToken(new UpdateFcmTokenRequestDto(token))
                        .execute();

                if (resp.isSuccessful()) {
                    Log.d(TAG, "FCM token registered with backend");
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "FCM_TOKEN_UPDATE_FAILED");
                    Log.w(TAG, "FCM token update failed: " + apiError.getCode());
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "FCM token update network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @Override
    public void requestPasswordReset(String email, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = authApiService
                        .requestPasswordReset(new com.walkmate.data.datasource.remote.dto.request.user.RequestPasswordResetDto(email))
                        .execute();

                if (resp.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "REQUEST_RESET_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "requestPasswordReset network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void verifyPasswordReset(String email, String otp, DomainCallback<String> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<com.walkmate.data.datasource.remote.dto.response.user.PasswordResetTokenDto>> resp = authApiService
                        .verifyPasswordReset(new com.walkmate.data.datasource.remote.dto.request.user.VerifyPasswordResetDto(email, otp))
                        .execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(resp.body().getData().getResetToken());
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "VERIFY_OTP_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "verifyPasswordReset network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void confirmPasswordReset(String resetToken, String newPassword, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = authApiService
                        .confirmPasswordReset(new com.walkmate.data.datasource.remote.dto.request.user.ConfirmPasswordResetDto(resetToken, newPassword))
                        .execute();

                if (resp.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "CONFIRM_RESET_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "confirmPasswordReset network error", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Exposes SessionManager for other repositories that may need it
     * (e.g., to build an authenticated Retrofit instance).
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private <T> String extractErrorCode(ApiResponse<T> body, String fallback) {
        if (body != null && body.getError() != null && body.getError().getCode() != null) {
            return body.getError().getCode();
        }
        return fallback;
    }
}
