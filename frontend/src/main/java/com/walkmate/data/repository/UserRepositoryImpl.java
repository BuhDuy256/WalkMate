package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.AuthApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.request.user.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.RegisterRequestDto;
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
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public UserRepositoryImpl(Context context) {
        this.sessionManager = new SessionManager(context);
        this.authApiService = ApiClient.getAuthApiService();
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void login(String email, String password, DomainCallback<String> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<LoginResponseDto>> resp =
                        authApiService.login(new LoginRequestDto(email, password)).execute();

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
    public void register(String fullname, String email, String password, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<RegisterResponseDto>> resp =
                        authApiService.register(new RegisterRequestDto(fullname, email, password)).execute();

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
