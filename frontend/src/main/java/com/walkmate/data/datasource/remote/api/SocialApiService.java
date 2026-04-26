package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.social.FriendRequestResponse;
import com.walkmate.data.datasource.remote.dto.response.social.PublicUserResponse;
import com.walkmate.data.datasource.remote.dto.response.social.UserSummaryResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SocialApiService {

    // ── Friends ───────────────────────────────────────────────────────────────

    @GET("api/v1/friends")
    Call<ApiResponse<List<UserSummaryResponse>>> getFriends();

    @POST("api/v1/friends/{userId}/request")
    Call<ApiResponse<Void>> sendFriendRequest(@Path("userId") String userId);

    @POST("api/v1/friends/requests/{id}/accept")
    Call<ApiResponse<Void>> acceptFriendRequest(@Path("id") String id);

    @POST("api/v1/friends/requests/{id}/decline")
    Call<ApiResponse<Void>> declineFriendRequest(@Path("id") String id);

    @GET("api/v1/friends/requests/incoming")
    Call<ApiResponse<List<FriendRequestResponse>>> getIncomingRequests();

    @GET("api/v1/friends/requests/outgoing")
    Call<ApiResponse<List<FriendRequestResponse>>> getOutgoingRequests();

    @DELETE("api/v1/friends/{userId}")
    Call<ApiResponse<Void>> removeFriend(@Path("userId") String userId);

    @DELETE("api/v1/friends/requests/{id}")
    Call<ApiResponse<Void>> cancelFriendRequest(@Path("id") String id);

    @GET("api/v1/users/{userId}")
    Call<ApiResponse<PublicUserResponse>> getPublicProfile(@Path("userId") String userId);

    // ── Block ─────────────────────────────────────────────────────────────────

    @POST("api/v1/users/{userId}/block")
    Call<ApiResponse<Void>> block(@Path("userId") String userId);

    @DELETE("api/v1/users/{userId}/block")
    Call<ApiResponse<Void>> unblock(@Path("userId") String userId);

    @GET("api/v1/users/me/blocked")
    Call<ApiResponse<List<UserSummaryResponse>>> getBlockedUsers();
}
