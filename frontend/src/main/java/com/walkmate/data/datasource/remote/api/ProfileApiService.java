package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.ProfileAvatarUploadResponseDto;
import com.walkmate.data.datasource.remote.dto.ProfileResponseDto;
import com.walkmate.data.datasource.remote.dto.ProfileSetupAckResponseDto;
import com.walkmate.data.datasource.remote.dto.SetupProfileRequestDto;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

import java.util.UUID;

public interface ProfileApiService {
    @Multipart
    @POST("/api/profiles/{userId}/avatar")
    Call<ProfileAvatarUploadResponseDto> uploadAvatar(@Path("userId") UUID userId, @Part MultipartBody.Part file);

    @PATCH("/api/profiles/{userId}")
    Call<ProfileSetupAckResponseDto> setupProfile(@Path("userId") UUID userId, @Body SetupProfileRequestDto request);

    @GET("/api/profiles/{userId}")
    Call<ProfileResponseDto> getProfile(@Path("userId") UUID userId, @Query("viewerId") UUID viewerId);
}
