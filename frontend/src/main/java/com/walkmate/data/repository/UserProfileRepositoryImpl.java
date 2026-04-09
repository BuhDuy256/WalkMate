package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.UserProfileApiService;
import com.walkmate.data.datasource.remote.dto.request.user.UpdateProfileRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.AvatarUploadResponse;
import com.walkmate.data.datasource.remote.dto.response.user.UserProfileResponse;
import com.walkmate.data.mapper.UserProfileMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

public class UserProfileRepositoryImpl implements UserProfileRepository {

    private static final String TAG = "UserProfileRepo";

    private final SessionManager sessionManager;
    private final UserProfileApiService apiService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public UserProfileRepositoryImpl(Context context) {
        this.sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(UserProfileApiService.class);
    }

    // ── Interface implementation ──────────────────────────────────────────────

    @Override
    public void getMyProfile(DomainCallback<UserProfile> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<UserProfileResponse>> resp = apiService.getMyProfile().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(UserProfileMapper.toDomain(resp.body().getData()));
                } else {
                    if (resp.code() == 401) {
                        sessionManager.clearSession();
                        callback.onError(new Exception("AUTH_UNAUTHORIZED"));
                        return;
                    }
                    callback.onError(new Exception(extractErrorCode(resp.body(), "PROFILE_FETCH_FAILED")));
                }
            } catch (IOException e) {
                Log.e(TAG, "getMyProfile network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getProfile(String userId, DomainCallback<UserProfile> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<UserProfileResponse>> resp = apiService.getPublicProfile(userId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(UserProfileMapper.toDomain(resp.body().getData()));
                } else {
                    callback.onError(new Exception(extractErrorCode(resp.body(), "PROFILE_FETCH_FAILED")));
                }
            } catch (IOException e) {
                Log.e(TAG, "getProfile network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void updateProfile(String fullName, String gender, String dateOfBirth,
            String bio, int searchRadius, List<String> tags,
            DomainCallback<UserProfile> callback) {
        executor.execute(() -> {
            try {
                UpdateProfileRequestDto dto = new UpdateProfileRequestDto(
                        fullName, gender, dateOfBirth, bio, searchRadius, tags);

                Response<ApiResponse<UserProfileResponse>> resp = apiService.updateMyProfile(dto).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(UserProfileMapper.toDomain(resp.body().getData()));
                } else {
                    if (resp.code() == 401) {
                        sessionManager.clearSession();
                        callback.onError(new Exception("AUTH_UNAUTHORIZED"));
                        return;
                    }
                    callback.onError(new Exception(extractErrorCode(resp.body(), "PROFILE_UPDATE_FAILED")));
                }
            } catch (IOException e) {
                Log.e(TAG, "updateProfile network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void uploadAvatar(byte[] imageBytes, String filename, String mimeType,
            DomainCallback<String> callback) {
        executor.execute(() -> {
            try {
                RequestBody requestBody = RequestBody.create(imageBytes, MediaType.parse(mimeType));
                MultipartBody.Part part = MultipartBody.Part.createFormData("file", filename, requestBody);

                Response<ApiResponse<AvatarUploadResponse>> resp = apiService.uploadAvatar(part).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(resp.body().getData().avatarUrl);
                } else {
                    if (resp.code() == 401) {
                        sessionManager.clearSession();
                        callback.onError(new Exception("AUTH_UNAUTHORIZED"));
                        return;
                    }
                    callback.onError(new Exception(extractErrorCode(resp.body(), "AVATAR_UPLOAD_FAILED")));
                }
            } catch (IOException e) {
                Log.e(TAG, "uploadAvatar network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private <T> String extractErrorCode(ApiResponse<T> body, String fallback) {
        if (body != null && body.getError() != null && body.getError().getCode() != null) {
            return body.getError().getCode();
        }
        return fallback;
    }
}
