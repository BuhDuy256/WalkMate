package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.HotspotApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.hotspot.HotspotResponse;
import com.walkmate.data.mapper.HotspotMapper;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * Real implementation backed by Retrofit.
 */
public class HotspotRepositoryImpl implements HotspotRepository {

    private static final String TAG = "HotspotRepositoryImpl";

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HotspotApiService hotspotApiService;

    public HotspotRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.hotspotApiService = ApiClient
                .buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(HotspotApiService.class);
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void getHotspots(DomainCallback<List<Hotspot>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<HotspotResponse>>> response = hotspotApiService.getHotspots().execute();
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<HotspotResponse> payload = response.body().getData();
                    if (payload == null) {
                        callback.onError(new Exception("HOTSPOT_EMPTY_RESPONSE"));
                        return;
                    }
                    callback.onSuccess(HotspotMapper.toDomainList(payload));
                } else {
                    callback.onError(new Exception(extractErrorCode(response.body(), "HOTSPOT_FETCH_FAILED")));
                }
            } catch (IOException e) {
                Log.e(TAG, "getHotspots network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getHotspotById(String id, DomainCallback<Hotspot> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<HotspotResponse>> response = hotspotApiService.getHotspotById(id).execute();
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    HotspotResponse payload = response.body().getData();
                    if (payload == null) {
                        callback.onError(new Exception("HOTSPOT_NOT_FOUND"));
                        return;
                    }
                    callback.onSuccess(HotspotMapper.toDomain(payload));
                } else {
                    callback.onError(new Exception(extractErrorCode(response.body(), "HOTSPOT_NOT_FOUND")));
                }
            } catch (IOException e) {
                Log.e(TAG, "getHotspotById network error", e);
                callback.onError(e);
            }
        });
    }

    private <T> String extractErrorCode(ApiResponse<T> body, String fallback) {
        if (body != null && body.getError() != null && body.getError().getCode() != null) {
            return body.getError().getCode();
        }
        return fallback;
    }
}
