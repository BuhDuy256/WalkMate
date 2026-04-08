package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.ReviewApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.request.review.SubmitReviewRequest;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.review.ReviewResponse;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.shared.DomainCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class ReviewRepositoryImpl implements ReviewRepository {

    private static final String TAG = "ReviewRepo";

    private final ReviewApiService apiService;
    private final ExecutorService  executor = Executors.newCachedThreadPool();

    public ReviewRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager)
                .create(ReviewApiService.class);
    }

    // ── Interface methods ─────────────────────────────────────────────────────

    @Override
    public void submitReview(String sessionId, int ratingStars, String comment,
                             DomainCallback<WalkReview> callback) {
        executor.execute(() -> {
            try {
                SubmitReviewRequest body = new SubmitReviewRequest(ratingStars, comment);
                Response<ApiResponse<ReviewResponse>> resp =
                        apiService.submitReview(sessionId, body).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(toDomain(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "REVIEW_SUBMIT_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "submitReview network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getReviewsForUser(String userId, DomainCallback<List<WalkReview>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<ReviewResponse>>> resp =
                        apiService.getReviewsForUser(userId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<ReviewResponse> data = resp.body().getData();
                    callback.onSuccess(toDomainList(data != null ? data : Collections.emptyList()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "REVIEWS_FETCH_FAILED");
                    if (resp.code() == 422) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getReviewsForUser network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static WalkReview toDomain(ReviewResponse r) {
        return new WalkReview(
                r.getReviewId(),
                r.getSessionId(),
                r.getReviewerId(),
                r.getRevieweeId(),
                r.getRatingStars(),
                r.getComment(),
                r.getCreatedAt()
        );
    }

    private static List<WalkReview> toDomainList(List<ReviewResponse> responses) {
        List<WalkReview> result = new ArrayList<>(responses.size());
        for (ReviewResponse r : responses) result.add(toDomain(r));
        return result;
    }

}
