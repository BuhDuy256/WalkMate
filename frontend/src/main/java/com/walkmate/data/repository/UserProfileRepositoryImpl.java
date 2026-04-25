package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.local.dao.UserProfileDao;
import com.walkmate.data.datasource.local.entity.UserProfileEntity;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.UserProfileApiService;
import com.walkmate.data.datasource.remote.dto.request.user.UpdateProfileRequestDto;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.AvatarUploadResponse;
import com.walkmate.data.datasource.remote.dto.response.user.ProfileTagResponse;
import com.walkmate.data.datasource.remote.dto.response.user.UserProfileResponse;
import com.walkmate.data.mapper.UserProfileEntityMapper;
import com.walkmate.data.mapper.UserProfileMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.ProfileTagMaster;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;

import java.io.IOException;
import java.util.ArrayList;
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
    private final UserProfileDao dao;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public UserProfileRepositoryImpl(Context context, UserProfileDao dao) {
        this.sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(UserProfileApiService.class);
        this.dao = dao;
    }

    // ── Interface implementation ──────────────────────────────────────────────

    /**
     * Offline-first fetch of the authenticated user's own profile.
     *
     * Phase A — instant cache read.
     * Phase B — silent background refresh.
     */
    @Override
    public void getMyProfile(DomainCallback<UserProfile> callback) {
        executor.execute(() -> {
            final boolean[] cacheDelivered = {false};

            String userId = sessionManager.getUserId();
            if (userId != null) {
                UserProfileEntity cached = dao.getProfileById(userId);
                if (cached != null) {
                    cacheDelivered[0] = true;
                    callback.onSuccess(UserProfileEntityMapper.toDomain(cached));
                }
            }

            try {
                Response<ApiResponse<UserProfileResponse>> resp = apiService.getMyProfile().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    UserProfile fresh = UserProfileMapper.toDomain(resp.body().getData());
                    dao.upsert(UserProfileEntityMapper.toEntity(fresh, System.currentTimeMillis()));
                    callback.onSuccess(fresh);
                } else {
                    if (resp.code() == 401) {
                        sessionManager.clearSession();
                        if (!cacheDelivered[0]) {
                            callback.onError(new Exception("AUTH_UNAUTHORIZED"));
                        }
                        return;
                    }
                    if (!cacheDelivered[0]) {
                        ApiError apiError = ErrorParser.extractApiError(resp, "PROFILE_FETCH_FAILED");
                        if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                            callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                        } else {
                            callback.onError(new Exception(apiError.getCode()));
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getMyProfile network error", e);
                if (!cacheDelivered[0]) {
                    callback.onError(e);
                }
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
                    ApiError apiError = ErrorParser.extractApiError(resp, "PROFILE_FETCH_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getProfile network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getMasterTags(DomainCallback<List<ProfileTagMaster>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<ProfileTagResponse>>> resp =
                        apiService.getMasterTags().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<ProfileTagResponse> raw = resp.body().getData();
                    List<ProfileTagMaster> result = new ArrayList<>(raw != null ? raw.size() : 0);
                    if (raw != null) {
                        for (ProfileTagResponse r : raw) {
                            result.add(new ProfileTagMaster(r.tagId, r.tagName));
                        }
                    }
                    callback.onSuccess(result);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "TAGS_FETCH_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "getMasterTags network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void updateProfile(String fullName, String gender, String dateOfBirth,
            String bio, int searchRadius, List<String> tagIds,
            DomainCallback<UserProfile> callback) {
        executor.execute(() -> {
            try {
                UpdateProfileRequestDto dto = new UpdateProfileRequestDto(
                        fullName, gender, dateOfBirth, bio, searchRadius, tagIds);

                Response<ApiResponse<UserProfileResponse>> resp = apiService.updateMyProfile(dto).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    UserProfile fresh = UserProfileMapper.toDomain(resp.body().getData());
                    dao.upsert(UserProfileEntityMapper.toEntity(fresh, System.currentTimeMillis()));
                    callback.onSuccess(fresh);
                } else {
                    if (resp.code() == 401) {
                        sessionManager.clearSession();
                        callback.onError(new Exception("AUTH_UNAUTHORIZED"));
                        return;
                    }
                    ApiError apiError = ErrorParser.extractApiError(resp, "PROFILE_UPDATE_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
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
                    ApiError apiError = ErrorParser.extractApiError(resp, "AVATAR_UPLOAD_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "uploadAvatar network error", e);
                callback.onError(e);
            }
        });
    }
}
