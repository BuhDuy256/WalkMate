package com.walkmate.data.repository;

import android.content.Context;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.HotspotApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.hotspot.HotspotResponse;
import com.walkmate.data.mapper.HotspotMapper;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HotspotRepositoryImpl implements HotspotRepository {

    private final HotspotApiService apiService;
    private final ExecutorService callbackExecutor;

    public HotspotRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager)
                .create(HotspotApiService.class);
        this.callbackExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void getHotspots(DomainCallback<List<Hotspot>> callback) {
        apiService.getHotspots().enqueue(new Callback<ApiResponse<List<HotspotResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<HotspotResponse>>> call,
                    Response<ApiResponse<List<HotspotResponse>>> response) {
                callbackExecutor.execute(() -> {
                    try {
                        ApiResponse<List<HotspotResponse>> body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess() && body.getData() != null) {
                            callback.onSuccess(HotspotMapper.toDomainList(body.getData()));
                            return;
                        }
                        callback.onError(new Exception(extractErrorMessage(body, "Failed to load hotspots")));
                    } catch (Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<List<HotspotResponse>>> call, Throwable t) {
                callbackExecutor.execute(
                        () -> callback.onError(new Exception("Network error: " + safeMessage(t), t)));
            }
        });
    }

    @Override
    public void getHotspotById(String id, DomainCallback<Hotspot> callback) {
        apiService.getHotspotById(id).enqueue(new Callback<ApiResponse<HotspotResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<HotspotResponse>> call,
                    Response<ApiResponse<HotspotResponse>> response) {
                callbackExecutor.execute(() -> {
                    try {
                        ApiResponse<HotspotResponse> body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess() && body.getData() != null) {
                            callback.onSuccess(HotspotMapper.toDomain(body.getData()));
                            return;
                        }
                        callback.onError(new Exception(extractErrorMessage(body, "Hotspot not found: " + id)));
                    } catch (Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<HotspotResponse>> call, Throwable t) {
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
