package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.NotificationApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.notification.NotificationResponse;
import com.walkmate.data.mapper.NotificationMapper;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

public class NotificationRepositoryImpl implements NotificationRepository {

    private static final String TAG = "NotificationRepoImpl";

    private final NotificationApiService apiService;

    public NotificationRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient
                .buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(NotificationApiService.class);
    }

    // ── Interface methods ─────────────────────────────────────────────────────

    @Override
    public void getNotifications(DomainCallback<List<Notification>> callback) {
        new Thread(() -> {
            try {
                Response<ApiResponse<List<NotificationResponse>>> response =
                        apiService.getNotifications().execute();

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<NotificationResponse> payload = response.body().getData();
                    callback.onSuccess(NotificationMapper.toDomainList(payload));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(response, "NOTIFICATION_FETCH_FAILED");
                    Log.w(TAG, "getNotifications failed: " + apiError.getCode());
                    if (response.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getNotifications network error", e);
                callback.onError(e);
            }
        }).start();
    }

    @Override
    public void markRead(String notificationId, DomainCallback<Void> callback) {
        new Thread(() -> {
            try {
                Response<ApiResponse<Void>> response =
                        apiService.markRead(notificationId).execute();

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(response, "NOTIFICATION_MARK_READ_FAILED");
                    Log.w(TAG, "markRead failed: " + apiError.getCode());
                    if (response.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "markRead network error", e);
                callback.onError(e);
            }
        }).start();
    }

}
