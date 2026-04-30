package com.walkmate.ui.badge;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.gamification.BadgeCatalogItem;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.shared.DomainCallback;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BadgeViewModel extends ViewModel {

    private final GamificationRepository gamificationRepo;
    private final String                 userId;

    private final MutableLiveData<BadgeUiState> uiState = new MutableLiveData<>();

    // ── Badge progress thresholds (keyed by badgeName) ────────────────────────
    // Encodes what stat to read and what threshold each badge requires.
    private enum StatType { SESSIONS, DISTANCE_KM, TRUST_SCORE }

    private static final Map<String, long[]> THRESHOLDS = new HashMap<>(); // [threshold]
    private static final Map<String, StatType> STAT_TYPES = new HashMap<>();

    static {
        THRESHOLDS.put("FIRST_WALK",     new long[]{1});
        THRESHOLDS.put("FIRST_FIVE",     new long[]{5});
        THRESHOLDS.put("CENTURY_STEPS",  new long[]{10});
        THRESHOLDS.put("FIRST_KM",       new long[]{1});
        THRESHOLDS.put("TEN_KM_WALKER",  new long[]{10});
        THRESHOLDS.put("FIFTY_KM_WALKER",new long[]{50});
        THRESHOLDS.put("TRUSTED_WALKER", new long[]{100});
        THRESHOLDS.put("HIGHLY_TRUSTED", new long[]{500});

        STAT_TYPES.put("FIRST_WALK",     StatType.SESSIONS);
        STAT_TYPES.put("FIRST_FIVE",     StatType.SESSIONS);
        STAT_TYPES.put("CENTURY_STEPS",  StatType.SESSIONS);
        STAT_TYPES.put("FIRST_KM",       StatType.DISTANCE_KM);
        STAT_TYPES.put("TEN_KM_WALKER",  StatType.DISTANCE_KM);
        STAT_TYPES.put("FIFTY_KM_WALKER",StatType.DISTANCE_KM);
        STAT_TYPES.put("TRUSTED_WALKER", StatType.TRUST_SCORE);
        STAT_TYPES.put("HIGHLY_TRUSTED", StatType.TRUST_SCORE);
    }

    public BadgeViewModel(GamificationRepository gamificationRepo, String userId) {
        this.gamificationRepo = gamificationRepo;
        this.userId           = userId;
    }

    public LiveData<BadgeUiState> getUiState() { return uiState; }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void load() {
        uiState.postValue(BadgeUiState.loading());

        final AtomicInteger                          doneCount    = new AtomicInteger(0);
        final AtomicReference<List<BadgeCatalogItem>> catalogRef  = new AtomicReference<>(Collections.emptyList());
        final AtomicReference<List<UserBadge>>        earnedRef   = new AtomicReference<>(Collections.emptyList());
        final AtomicReference<UserStats>              statsRef     = new AtomicReference<>(null);
        final AtomicReference<String>                 errorRef     = new AtomicReference<>(null);

        Runnable tryPublish = () -> {
            if (doneCount.incrementAndGet() < 3) return;
            String err = errorRef.get();
            if (err != null && catalogRef.get().isEmpty()) {
                uiState.postValue(BadgeUiState.error(err));
                return;
            }
            uiState.postValue(buildState(catalogRef.get(), earnedRef.get(), statsRef.get()));
        };

        gamificationRepo.getBadgeCatalog(new DomainCallback<List<BadgeCatalogItem>>() {
            @Override public void onSuccess(List<BadgeCatalogItem> items) {
                catalogRef.set(items != null ? items : Collections.emptyList());
                tryPublish.run();
            }
            @Override public void onError(Exception e) {
                errorRef.compareAndSet(null, "Failed to load badges");
                tryPublish.run();
            }
        });

        gamificationRepo.getBadges(userId, new DomainCallback<List<UserBadge>>() {
            @Override public void onSuccess(List<UserBadge> badges) {
                earnedRef.set(badges != null ? badges : Collections.emptyList());
                tryPublish.run();
            }
            @Override public void onError(Exception e) {
                tryPublish.run();
            }
        });

        gamificationRepo.getStats(userId, new DomainCallback<UserStats>() {
            @Override public void onSuccess(UserStats stats) {
                statsRef.set(stats);
                tryPublish.run();
            }
            @Override public void onError(Exception e) {
                tryPublish.run();
            }
        });
    }

    // ── State builder ─────────────────────────────────────────────────────────

    private BadgeUiState buildState(List<BadgeCatalogItem> catalog,
                                    List<UserBadge> earned,
                                    UserStats stats) {
        // Build a lookup: badgeName → UserBadge
        Map<String, UserBadge> earnedMap = new HashMap<>();
        for (UserBadge b : earned) earnedMap.put(b.getBadgeName(), b);

        // Group catalog entries by category, preserving insertion order
        Map<String, List<BadgeUiState.BadgeItem>> byCategory = new LinkedHashMap<>();

        for (BadgeCatalogItem item : catalog) {
            String cat = item.getCategory() != null ? item.getCategory() : "Other";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>());

            UserBadge earnedBadge = earnedMap.get(item.getName());
            boolean isEarned = earnedBadge != null;

            String earnedDate   = null;
            int    progressPct  = 0;
            String progressLabel = null;

            if (isEarned) {
                earnedDate = formatDate(earnedBadge.getAwardedAt());
            } else if (stats != null) {
                long[] threshold = THRESHOLDS.get(item.getName());
                StatType statType = STAT_TYPES.get(item.getName());
                if (threshold != null && statType != null) {
                    long current = 0;
                    long target  = threshold[0];
                    switch (statType) {
                        case SESSIONS:
                            current = stats.getCompletedSessions();
                            progressLabel = current + " / " + target + " walks";
                            break;
                        case DISTANCE_KM:
                            current = (long) stats.getTotalDistanceKm();
                            progressLabel = current + " / " + target + " km";
                            break;
                        case TRUST_SCORE:
                            current = stats.getTrustScore();
                            progressLabel = current + " / " + target + " pts";
                            break;
                    }
                    progressPct = (int) Math.min(100, (current * 100L) / Math.max(1, target));
                }
            }

            byCategory.get(cat).add(new BadgeUiState.BadgeItem(
                    item.getName(),
                    item.getDisplayName(),
                    item.getDescription(),
                    isEarned,
                    earnedDate,
                    item.getRarity() != null ? item.getRarity() : "common",
                    cat,
                    progressPct,
                    progressLabel
            ));
        }

        List<BadgeUiState.BadgeCategory> categories = new ArrayList<>();
        for (Map.Entry<String, List<BadgeUiState.BadgeItem>> entry : byCategory.entrySet()) {
            categories.add(new BadgeUiState.BadgeCategory(entry.getKey(), entry.getValue()));
        }

        return new BadgeUiState(false, null, categories, earnedMap.size(), catalog.size());
    }

    private static String formatDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return null;
        try {
            // Backend returns ISO-like timestamp e.g. "2026-04-20 10:30:00"
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = parser.parse(rawDate);
            if (date == null) return null;
            return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
        } catch (ParseException e) {
            // Try date-only format
            try {
                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = parser.parse(rawDate.substring(0, 10));
                if (date == null) return null;
                return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
            } catch (ParseException ex) {
                return null;
            }
        }
    }
}
