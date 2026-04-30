package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.GamificationApiService;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.gamification.BadgeCatalogResponse;
import com.walkmate.data.datasource.remote.dto.response.gamification.BadgeResponse;
import com.walkmate.data.datasource.remote.dto.response.gamification.UserStatsResponse;
import com.walkmate.domain.gamification.BadgeCatalogItem;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.shared.DomainCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class GamificationRepositoryImpl implements GamificationRepository {

    private static final String TAG = "GamificationRepo";

    private final GamificationApiService apiService;
    private final ExecutorService        executor = Executors.newCachedThreadPool();

    public GamificationRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(GamificationApiService.class);
    }

    // ── Interface methods ─────────────────────────────────────────────────────

    @Override
    public void getBadges(String userId, DomainCallback<List<UserBadge>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<BadgeResponse>>> resp =
                        apiService.getBadges(userId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<BadgeResponse> data = resp.body().getData();
                    callback.onSuccess(toBadgeDomainList(data != null ? data : Collections.emptyList()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "BADGES_FETCH_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getBadges network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getBadgeCatalog(DomainCallback<List<BadgeCatalogItem>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<BadgeCatalogResponse>>> resp =
                        apiService.getBadgeCatalog().execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<BadgeCatalogResponse> data = resp.body().getData();
                    callback.onSuccess(toCatalogDomainList(data != null ? data : Collections.emptyList()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "CATALOG_FETCH_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getBadgeCatalog network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getStats(String userId, DomainCallback<UserStats> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<UserStatsResponse>> resp =
                        apiService.getStats(userId).execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(toStatsDomain(resp.body().getData()));
                } else {
                    ApiError apiError = ErrorParser.extractApiError(resp, "STATS_FETCH_FAILED");
                    if ("VALIDATION_ERROR".equals(apiError.getCode())) {
                        callback.onError(new Exception("VALIDATION_ERROR|" + apiError.getMessage()));
                    } else {
                        callback.onError(new Exception(apiError.getCode()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "getStats network error", e);
                callback.onError(e);
            }
        });
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private static List<UserBadge> toBadgeDomainList(List<BadgeResponse> responses) {
        List<UserBadge> result = new ArrayList<>(responses.size());
        for (BadgeResponse r : responses) {
            result.add(new UserBadge(r.badgeName, r.displayName, r.description,
                    r.awardedAt, r.rarity, r.category));
        }
        return result;
    }

    private static List<BadgeCatalogItem> toCatalogDomainList(List<BadgeCatalogResponse> responses) {
        List<BadgeCatalogItem> result = new ArrayList<>(responses.size());
        for (BadgeCatalogResponse r : responses) {
            result.add(new BadgeCatalogItem(r.name, r.displayName, r.description,
                    r.rarity, r.category));
        }
        return result;
    }

    private static UserStats toStatsDomain(UserStatsResponse r) {
        return new UserStats(r.userId, r.totalPoints, r.totalDistanceKm,
                r.completedSessions, r.trustScore);
    }

}
