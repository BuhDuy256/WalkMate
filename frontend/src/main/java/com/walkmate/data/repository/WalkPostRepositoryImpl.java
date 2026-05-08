package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.WalkPostApiService;
import com.walkmate.data.datasource.remote.dto.request.walkpost.CreateWalkPostRequest;
import com.walkmate.data.datasource.remote.dto.request.walkpost.UpdateWalkPostVisibilityRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.walkpost.WalkPostResponse;
import com.walkmate.data.mapper.WalkPostMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkpost.PostVisibility;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.domain.walkpost.WalkPostRepository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class WalkPostRepositoryImpl implements WalkPostRepository {

    private static final String TAG = "WalkPostRepo";

    private final WalkPostApiService apiService;
    private final ExecutorService    executor = Executors.newCachedThreadPool();

    public WalkPostRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(WalkPostApiService.class);
    }

    @Override
    public void createPost(String sessionId, String caption, PostVisibility visibility,
                           boolean showCompanion, boolean showRouteMap, boolean showStats,
                           DomainCallback<WalkPost> callback) {
        executor.execute(() -> {
            try {
                CreateWalkPostRequest request = new CreateWalkPostRequest(
                        caption, visibility.name(), showCompanion, showRouteMap, showStats);
                Response<ApiResponse<WalkPostResponse>> resp =
                        apiService.createPost(sessionId, request).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(WalkPostMapper.toDomain(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "WALK_POST_CREATE_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "createPost network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getMyPosts(DomainCallback<List<WalkPost>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<WalkPostResponse>>> resp = apiService.getMyPosts().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<WalkPostResponse> data = resp.body().getData();
                    callback.onSuccess(WalkPostMapper.toDomainList(
                            data != null ? data : Collections.emptyList()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "WALK_POST_FETCH_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "getMyPosts network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getUserPosts(String userId, DomainCallback<List<WalkPost>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<WalkPostResponse>>> resp =
                        apiService.getUserPosts(userId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<WalkPostResponse> data = resp.body().getData();
                    callback.onSuccess(WalkPostMapper.toDomainList(
                            data != null ? data : Collections.emptyList()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "WALK_POST_FETCH_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "getUserPosts network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void updateVisibility(String postId, String visibility, DomainCallback<WalkPost> callback) {
        executor.execute(() -> {
            try {
                UpdateWalkPostVisibilityRequest request = new UpdateWalkPostVisibilityRequest(visibility);
                Response<ApiResponse<WalkPostResponse>> resp =
                        apiService.updateVisibility(postId, request).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(WalkPostMapper.toDomain(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "WALK_POST_UPDATE_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "updateVisibility network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void deletePost(String postId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.deletePost(postId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(null);
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "WALK_POST_DELETE_FAILED");
                    callback.onError(new Exception(apiError.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "deletePost network error", e);
                callback.onError(e);
            }
        });
    }
}
