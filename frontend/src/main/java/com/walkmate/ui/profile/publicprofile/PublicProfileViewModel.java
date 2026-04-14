package com.walkmate.ui.profile.publicprofile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.domain.DomainCallback;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.social.UserSummary;
import com.walkmate.ui.profile.ProfileUiState.Badge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ViewModel for the Public Profile screen.
 *
 * Data flow:
 *   loadProfile(userId) → posts loading → fires 4 parallel calls:
 *     getPublicProfile(), getBadges(), getStats(), getReviewsForUser()
 *   → merges all results into a single PublicProfileUiState → postValue().
 *
 * The isSelf flag is true when the viewed userId matches the stored local user ID,
 * which hides all friendship action buttons.
 *
 * Friendship mutations (sendFriendRequest, removeFriend, etc.) reload the
 * profile on success so the UI reflects the new friendship state.
 */
public class PublicProfileViewModel extends ViewModel {

    private final MutableLiveData<PublicProfileUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> navigateBackEvent     = new MutableLiveData<>();

    private final SocialRepository       socialRepo;
    private final GamificationRepository gamificationRepo;
    private final ReviewRepository       reviewRepo;
    private final String                 localUserId;

    // Last-loaded userId for refresh after mutations.
    private String currentUserId;

    public PublicProfileViewModel(SocialRepository socialRepo,
                                  GamificationRepository gamificationRepo,
                                  ReviewRepository reviewRepo,
                                  String localUserId) {
        this.socialRepo       = socialRepo;
        this.gamificationRepo = gamificationRepo;
        this.reviewRepo       = reviewRepo;
        this.localUserId      = localUserId;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<PublicProfileUiState> getUiState() { return uiState; }

    public LiveData<Boolean> getNavigateBackEvent() { return navigateBackEvent; }

    public void consumeNavigateBackEvent() { navigateBackEvent.postValue(false); }

    /**
     * Loads a user's public profile. Fires 4 parallel calls and merges results
     * into one state once all complete. isSelf is set when userId equals the
     * local user's JWT sub claim.
     */
    public void loadProfile(String userId) {
        currentUserId = userId;
        uiState.postValue(PublicProfileUiState.loading());

        final boolean isSelf = userId != null && userId.equals(localUserId);

        final AtomicReference<UserSummary>      profileHolder = new AtomicReference<>(null);
        final AtomicReference<List<Badge>>      badgesHolder  = new AtomicReference<>(Collections.emptyList());
        final AtomicReference<UserStats>        statsHolder   = new AtomicReference<>(null);
        final AtomicReference<List<WalkReview>> reviewsHolder = new AtomicReference<>(Collections.emptyList());

        final AtomicInteger doneCount = new AtomicInteger(0);

        Runnable publish = () -> {
            if (doneCount.incrementAndGet() == 4) {
                UserSummary   prof   = profileHolder.get();
                String        status = prof != null ? prof.getFriendshipStatus() : "NONE";
                uiState.postValue(new PublicProfileUiState(
                        false, null,
                        prof,
                        badgesHolder.get(),
                        statsHolder.get(),
                        reviewsHolder.get(),
                        status != null ? status : "NONE",
                        isSelf));
            }
        };

        // ── Profile ───────────────────────────────────────────────────────────
        socialRepo.getPublicProfile(userId, new DomainCallback<UserSummary>() {
            @Override
            public void onSuccess(UserSummary profile) {
                profileHolder.set(profile);
                publish.run();
            }
            @Override
            public void onError(Exception e) {
                // Profile failure is fatal — show error state immediately.
                uiState.postValue(PublicProfileUiState.error(friendlyError(e)));
                // Absorb remaining callbacks by inflating doneCount past the threshold.
                doneCount.addAndGet(4);
            }
        });

        // ── Badges ────────────────────────────────────────────────────────────
        gamificationRepo.getBadges(userId, new DomainCallback<List<UserBadge>>() {
            @Override
            public void onSuccess(List<UserBadge> userBadges) {
                badgesHolder.set(mapBadges(userBadges));
                publish.run();
            }
            @Override
            public void onError(Exception e) {
                // Non-fatal — badges section shows empty.
                publish.run();
            }
        });

        // ── Stats ─────────────────────────────────────────────────────────────
        gamificationRepo.getStats(userId, new DomainCallback<UserStats>() {
            @Override
            public void onSuccess(UserStats stats) {
                statsHolder.set(stats);
                publish.run();
            }
            @Override
            public void onError(Exception e) {
                // Non-fatal — stats show "—".
                publish.run();
            }
        });

        // ── Reviews ───────────────────────────────────────────────────────────
        reviewRepo.getReviewsForUser(userId, new DomainCallback<List<WalkReview>>() {
            @Override
            public void onSuccess(List<WalkReview> reviews) {
                reviewsHolder.set(reviews != null ? reviews : Collections.emptyList());
                publish.run();
            }
            @Override
            public void onError(Exception e) {
                // Non-fatal — reviews feed shows empty.
                publish.run();
            }
        });
    }

    public void sendFriendRequest(String userId) {
        socialRepo.sendFriendRequest(userId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v)      { reloadCurrent(); }
            @Override public void onError(Exception e)   { postError(e); }
        });
    }

    public void removeFriend(String userId) {
        socialRepo.removeFriend(userId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v)      { reloadCurrent(); }
            @Override public void onError(Exception e)   { postError(e); }
        });
    }

    public void acceptIncomingRequest(String requestId) {
        socialRepo.acceptFriendRequest(requestId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v)      { reloadCurrent(); }
            @Override public void onError(Exception e)   { postError(e); }
        });
    }

    public void declineIncomingRequest(String requestId) {
        socialRepo.declineFriendRequest(requestId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v)      { reloadCurrent(); }
            @Override public void onError(Exception e)   { postError(e); }
        });
    }

    /**
     * Blocks a user, then triggers navigation back so the Fragment pops itself.
     */
    public void blockUser(String userId) {
        socialRepo.block(userId, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
                navigateBackEvent.postValue(true);
            }
            @Override
            public void onError(Exception e) {
                postError(e);
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void reloadCurrent() {
        if (currentUserId != null) loadProfile(currentUserId);
    }

    private void postError(Exception e) {
        uiState.postValue(PublicProfileUiState.error(friendlyError(e)));
    }

    private static List<Badge> mapBadges(List<UserBadge> userBadges) {
        if (userBadges == null) return Collections.emptyList();
        List<Badge> result = new ArrayList<>(userBadges.size());
        for (UserBadge ub : userBadges) {
            result.add(new Badge(formatBadgeName(ub.getBadgeName()), 0));
        }
        return result;
    }

    /** Converts "FIRST_WALK" → "First Walk". */
    private static String formatBadgeName(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
              .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String friendlyError(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Something went wrong.";
    }
}
