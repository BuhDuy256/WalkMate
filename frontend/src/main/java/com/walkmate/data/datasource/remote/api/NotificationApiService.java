package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.notification.NotificationResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface NotificationApiService {

    @GET("api/v1/notifications")
    Call<ApiResponse<List<NotificationResponse>>> getNotifications();

    @POST("api/v1/notifications/{notificationId}/read")
    Call<ApiResponse<Void>> markRead(@Path("notificationId") String notificationId);
}
