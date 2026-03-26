package com.walkmate.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.AuthApiService;
import com.walkmate.data.datasource.remote.dto.ApiResponse;
import com.walkmate.data.datasource.remote.dto.LoginRequestDto;
import com.walkmate.data.datasource.remote.dto.LoginResponseDto;
import com.walkmate.data.datasource.remote.dto.RegisterRequestDto;
import com.walkmate.data.datasource.remote.dto.RegisterResponseDto;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepositoryImpl implements UserRepository {

    private static final String PREFS_AUTH = "walkmate_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";

    private final AuthApiService apiService;
    private final SharedPreferences prefs;

    public UserRepositoryImpl(Context context) {
        this.apiService = ApiClient.getAuthApiService();
        this.prefs = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE);
    }

    @Override
    public void login(String email, String password, DomainCallback<String> callback) {
        LoginRequestDto request = new LoginRequestDto(email, password);
        apiService.login(request).enqueue(new Callback<ApiResponse<LoginResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponseDto>> call, Response<ApiResponse<LoginResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    String token = response.body().getData().getAccessToken();
                    saveAccessToken(token);
                    callback.onSuccess(token);
                } else {
                    String errorMsg = (response.body() != null && response.body().getError() != null) 
                            ? response.body().getError().getMessage() : "Invalid credentials";
                    callback.onError(new Exception(errorMsg));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResponseDto>> call, Throwable t) {
                callback.onError(new Exception("Network error: " + t.getMessage()));
            }
        });
    }

    @Override
    public void register(String fullname, String email, String password, DomainCallback<User> callback) {
        RegisterRequestDto request = new RegisterRequestDto(fullname, email, password);
        apiService.register(request).enqueue(new Callback<ApiResponse<RegisterResponseDto>>() {
            @Override
            public void onResponse(Call<ApiResponse<RegisterResponseDto>> call, Response<ApiResponse<RegisterResponseDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    RegisterResponseDto dto = response.body().getData();
                    // Mapping DTO to Domain Model
                    User user = new User(dto.getId(), dto.getFullname(), dto.getEmail());
                    callback.onSuccess(user);
                } else {
                    String errorMsg = (response.body() != null && response.body().getError() != null) 
                            ? response.body().getError().getMessage() : "Registration failed";
                    callback.onError(new Exception(errorMsg));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<RegisterResponseDto>> call, Throwable t) {
                callback.onError(new Exception("Network error: " + t.getMessage()));
            }
        });
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
