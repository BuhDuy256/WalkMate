package com.walkmate.data.repository;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WalkIntentRepositoryImpl implements WalkIntentRepository {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void createIntent(String hotspotId, float timeStart, float timeEnd,
                             int ageMin, int ageMax, DomainCallback<WalkIntent> callback) {
        executor.execute(() -> {
            // TODO: Replace with Retrofit call — POST /api/v1/intents
            //       Request body: CreateWalkIntentRequest (via WalkIntentMapper.toRequest())
            //       Expected: ApiResponse<WalkIntentResponse> → map via WalkIntentMapper.toDomain()
            WalkIntent mockIntent = new WalkIntent(
                    UUID.randomUUID().toString(),
                    hotspotId,
                    "mock-user-id",
                    timeStart,
                    timeEnd,
                    ageMin,
                    ageMax,
                    "PENDING",
                    new Date().toString()
            );
            callback.onSuccess(mockIntent);
        });
    }

    @Override
    public void findMatch(String intentId, DomainCallback<WalkIntent> callback) {
        executor.execute(() -> {
            // TODO: Replace with Retrofit call — GET /api/v1/intents/{intentId}/match
            //       Expected: ApiResponse<WalkIntentResponse> → map via WalkIntentMapper.toDomain()
            WalkIntent matchedIntent = new WalkIntent(
                    intentId,
                    null,
                    "mock-user-id",
                    0f, 0f, 0, 0,
                    "MATCHED",
                    new Date().toString()
            );
            callback.onSuccess(matchedIntent);
        });
    }

    @Override
    public void cancelIntent(String intentId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            // TODO: Replace with Retrofit call — DELETE /api/v1/intents/{intentId}
            callback.onSuccess(null);
        });
    }
}
