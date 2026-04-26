package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.SocialApiService;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.social.FriendRequestResponse;
import com.walkmate.data.datasource.remote.dto.response.social.PublicUserResponse;
import com.walkmate.data.datasource.remote.dto.response.social.UserSummaryResponse;
import com.walkmate.data.mapper.SocialMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.social.FriendRequest;
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
                    deliverError(resp, "FRIENDS_FETCH_FAILED", callback);
                }
            } catch (IOException e) {
                Log.e(TAG, "getFriends network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void sendFriendRequest(String userId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.sendFriendRequest(userId).execute();
                handleVoidResponse(resp, "FRIEND_REQUEST_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "sendFriendRequest network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void acceptFriendRequest(String requestId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.acceptFriendRequest(requestId).execute();
                handleVoidResponse(resp, "ACCEPT_REQUEST_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "acceptFriendRequest network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void declineFriendRequest(String requestId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.declineFriendRequest(requestId).execute();
                handleVoidResponse(resp, "DECLINE_REQUEST_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "declineFriendRequest network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getIncomingRequests(DomainCallback<List<FriendRequest>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<FriendRequestResponse>>> resp =
                        apiService.getIncomingRequests().execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(SocialMapper.toFriendRequestList(resp.body().getData()));
                } else {
                    deliverError(resp, "INCOMING_REQUESTS_FAILED", callback);
                }
            } catch (IOException e) {
                Log.e(TAG, "getIncomingRequests network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getOutgoingRequests(DomainCallback<List<FriendRequest>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<FriendRequestResponse>>> resp =
                        apiService.getOutgoingRequests().execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(SocialMapper.toFriendRequestList(resp.body().getData()));
                } else {
                    deliverError(resp, "OUTGOING_REQUESTS_FAILED", callback);
                }
            } catch (IOException e) {
                Log.e(TAG, "getOutgoingRequests network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void removeFriend(String userId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.removeFriend(userId).execute();
                handleVoidResponse(resp, "REMOVE_FRIEND_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "removeFriend network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void cancelFriendRequest(String requestId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<Void>> resp = apiService.cancelFriendRequest(requestId).execute();
                handleVoidResponse(resp, "CANCEL_REQUEST_FAILED", callback);
            } catch (IOException e) {
                Log.e(TAG, "cancelFriendRequest network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getPublicProfile(String userId, DomainCallback<UserSummary> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<PublicUserResponse>> resp =
                        apiService.getPublicProfile(userId).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(SocialMapper.toUserSummary(resp.body().getData()));
                } else {
                    deliverError(resp, "PUBLIC_PROFILE_FAILED", callback);
                }
            } catch (IOException e) {
                Log.e(TAG, "getPublicProfile network error", e);
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

    @Override
    public void getBlockedUsers(DomainCallback<List<UserSummary>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<UserSummaryResponse>>> resp =
                        apiService.getBlockedUsers().execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(SocialMapper.toDomainList(resp.body().getData()));
                } else {
                    deliverError(resp, "BLOCKED_USERS_FAILED", callback);
                }
            } catch (IOException e) {
                Log.e(TAG, "getBlockedUsers network error", e);
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
            if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
            } else {
                callback.onError(new Exception(apiError.getCode()));
            }
        }
    }

    private <T> void deliverError(Response<?> resp, String fallbackCode,
                                   DomainCallback<T> callback) {
        ApiError apiError = ErrorParser.extractApiError(resp, fallbackCode);
        if ("VALIDATION_ERROR".equals(apiError.getCode())) {
            callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
        } else {
            callback.onError(new Exception(apiError.getCode()));
        }
    }
}
