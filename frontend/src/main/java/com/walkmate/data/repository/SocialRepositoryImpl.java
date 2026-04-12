package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.SocialApiService;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.social.UserSummaryResponse;
import com.walkmate.data.mapper.SocialMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.social.UserSummary;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class SocialRepositoryImpl implements SocialRepository {

    private static final String TAG = "SocialRepo";

    private final SocialApiService apiService;
    private final ExecutorService  executor = Executors.newCachedThreadPool();

    public SocialRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(SocialApiService.class);
    }

    // ── Friends ───────────────────────────────────────────────────────────────

    @Override
    public void getFriends(DomainCallback<List<UserSummary>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<UserSummaryResponse>>> resp =
                        apiService.getFriends().execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(SocialMapper.toDomainList(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "FRIENDS_FETCH_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getFriends network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Follow ────────────────────────────────────────────────────────────────

    @Override
    public void follow(String targetUserId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.follow(targetUserId).execute();
                handleVoidResponse(resp, "FOLLOW_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "follow network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void unfollow(String targetUserId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.unfollow(targetUserId).execute();
                handleVoidResponse(resp, "UNFOLLOW_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "unfollow network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getFollowers(String userId, DomainCallback<List<UserSummary>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<UserSummaryResponse>>> resp =
                        apiService.getFollowers(userId).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(SocialMapper.toDomainList(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "FOLLOWERS_FETCH_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getFollowers network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getFollowing(String userId, DomainCallback<List<UserSummary>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<UserSummaryResponse>>> resp =
                        apiService.getFollowing(userId).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(SocialMapper.toDomainList(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "FOLLOWING_FETCH_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getFollowing network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    @Override
    public void block(String targetUserId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.block(targetUserId).execute();
                handleVoidResponse(resp, "BLOCK_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "block network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void unblock(String targetUserId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.unblock(targetUserId).execute();
                handleVoidResponse(resp, "UNBLOCK_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "unblock network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void handleVoidResponse(Response<ApiResponse<Void>> resp,
                                    String fallbackCode,
                                    DomainCallback<Void> callback) {
        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
            callback.onSuccess(null);
        } else {
            ApiError apiError = ErrorParser.extractApiError(resp, fallbackCode);
            if (resp.code() == 422) {
                callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
            } else {
                callback.onError(new Exception(apiError.getCode()));
            }
        }
    }
}
