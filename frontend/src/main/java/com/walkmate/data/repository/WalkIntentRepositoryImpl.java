package com.walkmate.data.repository;

import android.content.Context;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MOCK implementation — bypasses Retrofit and returns hardcoded data with a
 * simulated 1-second network delay. Replace each method body with real Retrofit
 * calls (following the original pattern) when the backend is ready.
 */
public class WalkIntentRepositoryImpl implements WalkIntentRepository {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public WalkIntentRepositoryImpl(Context context) {
        // Context retained for future real implementation
    }

    // ---------------------------------------------------------------------------
    // Mock data
    // ---------------------------------------------------------------------------

    private static List<WalkIntent> buildMockIntents() {
        return Arrays.asList(
                new WalkIntent(
                        "intent-001",
                        "Công viên Tao Đàn",
                        "mock-user-1",
                        17.0f, 19.0f,
                        22, 35,
                        "OPEN",
                        "2026-03-29T10:00:00Z",
                        Arrays.asList("Đi bộ chậm", "Nghe podcast")),
                new WalkIntent(
                        "intent-002",
                        "Hồ Con Rùa",
                        "mock-user-1",
                        8.0f, 9.5f,
                        18, 40,
                        "OPEN",
                        "2026-03-29T09:00:00Z",
                        Arrays.asList("Chạy bộ", "Chụp ảnh")),
                new WalkIntent(
                        "intent-003",
                        "Công viên Gia Định",
                        "mock-user-1",
                        18.5f, 20.0f,
                        20, 30,
                        "OPEN",
                        "2026-03-29T11:00:00Z",
                        Arrays.asList("Thiền", "Đi bộ chậm"))
        );
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void createIntent(String hotspotId, float timeStart, float timeEnd,
                             int ageMin, int ageMax, DomainCallback<WalkIntent> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(new WalkIntent(
                    "intent-new-" + System.currentTimeMillis(),
                    hotspotId,
                    "mock-user-1",
                    timeStart, timeEnd,
                    ageMin, ageMax,
                    "OPEN",
                    "2026-03-29T12:00:00Z",
                    Collections.emptyList()));
        });
    }

    @Override
    public void listActiveIntents(DomainCallback<List<WalkIntent>> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(buildMockIntents());
        });
    }

    @Override
    public void findMatch(String intentId, DomainCallback<WalkIntent> callback) {
        executor.execute(() -> {
            sleep();
            callback.onError(new Exception("No match found yet"));
        });
    }

    @Override
    public void cancelIntent(String intentId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(null);
        });
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
