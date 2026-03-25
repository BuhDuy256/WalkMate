package com.walkmate.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.AuthApiService;
import com.walkmate.data.datasource.remote.dto.request.user.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.request.user.RegisterRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.LoginResponseDto;
import com.walkmate.data.datasource.remote.dto.response.user.RegisterResponseDto;
import com.walkmate.data.mapper.UserMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepositoryImpl implements UserRepository {

    private static final String PREFS_AUTH = "walkmate_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";

    private final AuthApiService apiService;
    private final SharedPreferences prefs;
    private final ExecutorService callbackExecutor;

    public UserRepositoryImpl(Context context) {
        this.apiService = ApiClient.getAuthApiService();
        this.prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
        this.callbackExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void login(String email, String password, DomainCallback<String> callback) {
        LoginRequestDto request = UserMapper.toLoginRequest(email, password);
        apiService.login(request).enqueue(new Callback<ApiResponse<LoginResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponseDto>> call,
                    Response<ApiResponse<LoginResponseDto>> response) {
                callbackExecutor.execute(() -> {
                    try {
                        ApiResponse<LoginResponseDto> body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess()) {
                            String token = UserMapper.toAccessToken(body.getData());
                            saveAccessToken(token);
                            callback.onSuccess(token);
                            return;
                        }

                        callback.onError(new Exception(extractErrorMessage(body, "Invalid credentials")));
                    } catch (Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResponseDto>> call, Throwable t) {
                callbackExecutor.execute(
                        () -> callback.onError(new Exception("Network error: " + safeMessage(t), t)));
            }
        });
    }

    @Override
    public void register(String fullname, String email, String password, DomainCallback<User> callback) {
        RegisterRequestDto request = UserMapper.toRegisterRequest(fullname, email, password);
        apiService.register(request).enqueue(new Callback<ApiResponse<RegisterResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<RegisterResponseDto>> call,
                    Response<ApiResponse<RegisterResponseDto>> response) {
                callbackExecutor.execute(() -> {
                    try {
                        ApiResponse<RegisterResponseDto> body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess()) {
                            User user = UserMapper.toDomainUser(body.getData());
                            callback.onSuccess(user);
                            return;
                        }

                        callback.onError(new Exception(extractErrorMessage(body, "Registration failed")));
                    } catch (Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<RegisterResponseDto>> call, Throwable t) {
                callbackExecutor.execute(
                        () -> callback.onError(new Exception("Network error: " + safeMessage(t), t)));
            }
        });
    }

    private static String extractErrorMessage(ApiResponse<?> responseBody, String fallback) {
        if (responseBody != null && responseBody.getError() != null) {
            String message = responseBody.getError().getMessage();
            if (message != null && !message.trim().isEmpty()) {
                return message;
            }
        }
        return fallback;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return "Unknown network error";
        }
        return throwable.getMessage();
    }

    @Override
    public void saveAccessToken(String token) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply();
    }

    @Override
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }
}
