package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.AuthApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.UserApiService;
import com.walkmate.data.datasource.remote.dto.request.user.GoogleLoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.RegisterRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.UpdateFcmTokenRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.LoginResponseDto;
import com.walkmate.data.datasource.remote.dto.response.user.RegisterResponseDto;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class UserRepositoryImpl implements UserRepository {

    private static final String TAG = "UserRepositoryImpl";

    private final SessionManager sessionManager;
    private final AuthApiService authApiService;
    private final UserApiService userApiService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public UserRepositoryImpl(Context context) {
        this.sessionManager = new SessionManager(context);
        this.authApiService = ApiClient.getAuthApiService();
        // FCM token update is an authenticated call — use the authenticated Retrofit
        // client.
        this.userApiService = ApiClient.buildAuthenticatedRetrofit(sessionManager)
                .create(UserApiService.class);
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void login(String email, String password, DomainCallback<String> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<LoginResponseDto>> resp = authApiService
                        .login(new LoginRequestDto(email, password)).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    String token = resp.body().getData().getAccessToken();
                    saveAccessToken(token);
                    Log.d(TAG, "real login succeeded");
                    callback.onSuccess(token);
                } else {
                    String code = extractErrorCode(resp.body(), "USER_INVALID_CREDENTIALS");
                    Log.w(TAG, "login failed: " + code);
                    callback.onError(new Exception(code));
                }
            } catch (IOException e) {
                Log.e(TAG, "login network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void loginWithGoogle(String googleIdToken, DomainCallback<String> callback) {
        // Step 1: Sign in to Firebase with the Google ID token to get a Firebase ID token
        AuthCredential credential = GoogleAuthProvider.getCredential(googleIdToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    // Step 2: Get the Firebase ID token
                    authResult.getUser().getIdToken(false)
                            .addOnSuccessListener(tokenResult -> {
                                String firebaseIdToken = tokenResult.getToken();
                                // Step 3: Exchange Firebase ID token for WalkMate JWT on background thread
                                executor.execute(() -> {
                                    try {
                                        Response<ApiResponse<LoginResponseDto>> resp = authApiService
                                                .loginWithGoogle(new GoogleLoginRequestDto(firebaseIdToken))
                                                .execute();

                                        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                                            String token = resp.body().getData().getAccessToken();
                                            saveAccessToken(token);
                                            Log.d(TAG, "Google login succeeded");
                                            callback.onSuccess(token);
                                        } else {
                                            String code = extractErrorCode(resp.body(), "GOOGLE_LOGIN_FAILED");
                                            Log.w(TAG, "Google backend login failed: " + code);
                                            callback.onError(new Exception(code));
                                        }
                                    } catch (IOException e) {
                                        Log.e(TAG, "Google login network error", e);
                                        callback.onError(e);
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to get Firebase ID token", e);
                                callback.onError(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase signInWithCredential failed", e);
                    callback.onError(e);
                });
    }

    @Override
    public void register(String fullname, String email, String password, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<RegisterResponseDto>> resp = authApiService
                        .register(new RegisterRequestDto(fullname, email, password)).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    Log.d(TAG, "real register succeeded");
                    callback.onSuccess(null);
                } else {
                    String code = extractErrorCode(resp.body(), "REGISTER_FAILED");
                    Log.w(TAG, "register failed: " + code);
                    callback.onError(new Exception(code));
                }
            } catch (IOException e) {
                Log.e(TAG, "register network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void saveAccessToken(String token) {
        sessionManager.saveAccessToken(token);
    }

    @Override
    public String getAccessToken() {
        return sessionManager.getAccessToken();
    }

    /**
     * Sends the FCM device token to the backend so it can push to this device.
     *
     * Guard: if the user has not yet logged in (no access token stored), the call
     * is skipped entirely. FCM will fire onNewToken() again the next time it
     * rotates the token, so no data is permanently lost.
     */
    @Override
    public void updateFcmToken(String token, DomainCallback<Void> callback) {
        if (!sessionManager.hasUsableAccessToken()) {
            Log.d(TAG, "updateFcmToken skipped — user not authenticated");
            return;
        }
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = userApiService.updateFcmToken(new UpdateFcmTokenRequestDto(token))
                        .execute();

                if (resp.isSuccessful()) {
                    Log.d(TAG, "FCM token registered with backend");
                    callback.onSuccess(null);
                } else {
                    String code = extractErrorCode(resp.body(), "FCM_TOKEN_UPDATE_FAILED");
                    Log.w(TAG, "FCM token update failed: " + code);
                    callback.onError(new Exception(code));
                }
            } catch (IOException e) {
                Log.e(TAG, "FCM token update network error", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Exposes SessionManager for repositories that may need it in the real
     * implementation (e.g., to build an authenticated Retrofit instance).
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private <T> String extractErrorCode(ApiResponse<T> body, String fallback) {
        if (body != null && body.getError() != null && body.getError().getCode() != null) {
            return body.getError().getCode();
        }
        return fallback;
    }
}
