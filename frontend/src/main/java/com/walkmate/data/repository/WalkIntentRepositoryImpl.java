package com.walkmate.data.repository;

import android.content.Context;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.WalkIntentApiService;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.walkintent.WalkIntentResponse;
import com.walkmate.data.mapper.WalkIntentMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WalkIntentRepositoryImpl implements WalkIntentRepository {

    private final WalkIntentApiService apiService;
    private final SessionManager sessionManager;
    private final ExecutorService callbackExecutor;

    public WalkIntentRepositoryImpl(Context context) {
        this.sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager)
                .create(WalkIntentApiService.class);
        this.callbackExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void createIntent(String hotspotId, float timeStart, float timeEnd,
            int ageMin, int ageMax, DomainCallback<WalkIntent> callback) {
        var request = WalkIntentMapper.toRequest(hotspotId, timeStart, timeEnd, ageMin, ageMax);

        apiService.createIntent(request).enqueue(new Callback<ApiResponse<WalkIntentResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<WalkIntentResponse>> call,
                    Response<ApiResponse<WalkIntentResponse>> response) {
                callbackExecutor.execute(() -> {
                    try {
                        ApiResponse<WalkIntentResponse> body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess() && body.getData() != null) {
                            callback.onSuccess(WalkIntentMapper.toDomain(body.getData()));
                            return;
                        }
                        callback.onError(new Exception(extractErrorMessage(body, "Failed to create intent")));
                    } catch (Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<WalkIntentResponse>> call, Throwable t) {
                callbackExecutor.execute(
                        () -> callback.onError(new Exception("Network error: " + safeMessage(t), t)));
            }
        });
    }

    @Override
    public void findMatch(String intentId, DomainCallback<WalkIntent> callback) {
        apiService.findMatch(intentId).enqueue(new Callback<ApiResponse<WalkIntentResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<WalkIntentResponse>> call,
                    Response<ApiResponse<WalkIntentResponse>> response) {
                callbackExecutor.execute(() -> {
                    try {
                        if (response.code() == 204) {
                            callback.onError(new Exception("No match found yet"));
                            return;
                        }

                        ApiResponse<WalkIntentResponse> body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess() && body.getData() != null) {
                            callback.onSuccess(WalkIntentMapper.toDomain(body.getData()));
                            return;
                        }
                        callback.onError(new Exception(extractErrorMessage(body, "Failed to find match")));
                    } catch (Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<WalkIntentResponse>> call, Throwable t) {
                callbackExecutor.execute(
                        () -> callback.onError(new Exception("Network error: " + safeMessage(t), t)));
            }
        });
    }

    @Override
    public void cancelIntent(String intentId, DomainCallback<Void> callback) {
        apiService.cancelIntent(intentId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                callbackExecutor.execute(() -> {
                    try {
                        ApiResponse<Void> body = response.body();
                        if (response.isSuccessful() && (body == null || body.isSuccess())) {
                            callback.onSuccess(null);
                            return;
                        }
                        callback.onError(new Exception(extractErrorMessage(body, "Failed to cancel intent")));
                    } catch (Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
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
}
