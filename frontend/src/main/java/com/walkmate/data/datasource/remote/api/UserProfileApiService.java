package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.user.UpdateProfileRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.AvatarUploadResponse;
import com.walkmate.data.datasource.remote.dto.response.user.ProfileTagResponse;
import com.walkmate.data.datasource.remote.dto.response.user.UserProfileResponse;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface UserProfileApiService {

    @GET("api/v1/profile/me")
    Call<ApiResponse<UserProfileResponse>> getMyProfile();

    @GET("api/v1/profile/tags")
    Call<ApiResponse<List<ProfileTagResponse>>> getMasterTags();

    @PUT("api/v1/profile/me")
    Call<ApiResponse<UserProfileResponse>> updateMyProfile(@Body UpdateProfileRequestDto request);

    @Multipart
    @POST("api/v1/profile/avatar")
    Call<ApiResponse<AvatarUploadResponse>> uploadAvatar(@Part MultipartBody.Part file);

    @GET("api/v1/users/{userId}")
    Call<ApiResponse<UserProfileResponse>> getPublicProfile(@Path("userId") String userId);
}
