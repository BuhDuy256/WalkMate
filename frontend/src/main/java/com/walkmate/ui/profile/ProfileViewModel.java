package com.walkmate.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.event.AuthEventBus;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.user.VisibilityMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ViewModel for the Profile screen.
 *
 * Data flow:
 *   loadProfile() → posts loading state → fetches base profile
 *   → on profile success, fires 3 parallel calls (badges, stats, reviews)
 *   → merges all results into a single ProfileUiState → postValue().
 *
 * Edit flow:
 *   saveProfile(…) → calls updateProfile() → reloads profile on success.
 *   uploadAvatar(…) → calls uploadAvatar() → reloads profile on success.
 *   setVisibility(…) → calls UserRepository.setVisibility() → reloads profile on success.
 *   logoutAll() → calls UserRepository.logoutAll().
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<ProfileUiState> uiState = new MutableLiveData<>();
    private final UserProfileRepository profileRepo;
    private final UserRepository userRepository;
    private final GamificationRepository gamificationRepo;
    private final ReviewRepository       reviewRepo;

    public ProfileViewModel(UserProfileRepository profileRepo,
                            UserRepository userRepository,
                            GamificationRepository gamificationRepo,
                            ReviewRepository reviewRepo) {
        this.profileRepo      = profileRepo;
        this.userRepository   = userRepository;
        this.gamificationRepo = gamificationRepo;
        this.reviewRepo       = reviewRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<ProfileUiState> getUiState() {
        return uiState;
    }

    /**
     * Loads the authenticated user's full profile.
     *
     * Phase 1 — base profile: fetched first (needed for userId).
     * Phase 2 — 3 parallel calls keyed on userId: badges, stats, reviews.
     * All 3 failures are non-fatal; the last-completed call publishes final state.
     */
    public void loadProfile() {
        uiState.postValue(ProfileUiState.loading());

        profileRepo.getMyProfile(new DomainCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                loadSupplementalData(profile);
            }

            @Override
            public void onError(Exception e) {
                uiState.postValue(ProfileUiState.error(friendlyError(e)));
            }
        });
    }

    /**
     * Persists profile changes to the backend then reloads the profile.
     */
    public void saveProfile(String fullName, String gender, String dateOfBirth,
                            String bio, int searchRadius, List<String> tags) {
        uiState.postValue(ProfileUiState.loading());

        profileRepo.updateProfile(fullName, gender, dateOfBirth, bio, searchRadius, tags,
                new DomainCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        loadSupplementalData(profile);
                    }

                    @Override
                    public void onError(Exception e) {
                        uiState.postValue(ProfileUiState.error(friendlyError(e)));
                    }
                });
    }

    /**
     * Uploads a new avatar image then reloads the profile.
     */
    public void uploadAvatar(byte[] imageBytes, String filename, String mimeType) {
        profileRepo.uploadAvatar(imageBytes, filename, mimeType,
                new DomainCallback<String>() {
                    @Override
                    public void onSuccess(String avatarUrl) {
                        loadProfile();
                    }

                    @Override
                    public void onError(Exception e) {
                        uiState.postValue(ProfileUiState.error(friendlyError(e)));
                    }
                });
    }

    /**
     * Sets the user's profile visibility and reloads the profile on success.
     */
    public void setVisibility(VisibilityMode mode) {
        userRepository.setVisibility(mode, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadProfile();
            }

            @Override
            public void onError(Exception e) {
                // SILENT errors (already public/private) are swallowed — no UI change needed.
                // TOAST errors show a transient message via the error field.
                uiState.postValue(ProfileUiState.error(friendlyError(e)));
            }
        });
    }

    /**
     * Logs out the user from all devices. Clears the session in UserRepositoryImpl, then
     * posts FORCE_LOGOUT on AuthEventBus so MainActivity can relaunch AuthActivity.
     */
    public void logoutAll() {
        userRepository.logoutAll(new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Session already cleared by UserRepositoryImpl.
                // Signal MainActivity to navigate to AuthActivity.
                AuthEventBus.getInstance().postForceLogout();
            }

            @Override
            public void onError(Exception e) {
                uiState.postValue(ProfileUiState.error(friendlyError(e)));
            }
        });
    }

    // ── Navigation signals ────────────────────────────────────────────────────

    private final MutableLiveData<Boolean> navigateToEditEvent = new MutableLiveData<>();

    public LiveData<Boolean> getNavigateToEditEvent() {
        return navigateToEditEvent;
    }

    /** Called by ProfileFragment once the navigation has been handled. */
    public void consumeNavigateToEdit() {
        navigateToEditEvent.setValue(false);
    }

    public void onEditProfileClicked() {
        navigateToEditEvent.postValue(true);
    }

    private final MutableLiveData<Boolean> navigateToHistoryEvent = new MutableLiveData<>();

    public LiveData<Boolean> getNavigateToHistoryEvent() {
        return navigateToHistoryEvent;
    }

    public void consumeNavigateToHistory() {
        navigateToHistoryEvent.setValue(false);
    }

    public void onWalkHistoryClicked() {
        navigateToHistoryEvent.postValue(true);
    }

    public void onMyBadgesClicked()    { /* Phase D: emit navigation signal */ }
    public void onSettingsClicked()    { /* Phase D: emit navigation signal */ }

    private final MutableLiveData<Boolean> navigateToFriendsEvent = new MutableLiveData<>();

    public LiveData<Boolean> getNavigateToFriendsEvent() {
        return navigateToFriendsEvent;
    }

    public void consumeNavigateToFriends() {
        navigateToFriendsEvent.setValue(false);
    }

    public void onFriendsClicked() {
        navigateToFriendsEvent.postValue(true);
    }

    private final MutableLiveData<Boolean> navigateToBlockedUsersEvent = new MutableLiveData<>();

    public LiveData<Boolean> getNavigateToBlockedUsersEvent() {
        return navigateToBlockedUsersEvent;
    }

    public void consumeNavigateToBlockedUsers() {
        navigateToBlockedUsersEvent.setValue(false);
    }

    public void onBlockedUsersClicked() {
        navigateToBlockedUsersEvent.postValue(true);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * After the base profile is available, fires badges, stats, and reviews in
     * parallel. AtomicInteger(3) acts as a barrier: the last call to return
     * publishes the merged final state.
     */
    private void loadSupplementalData(UserProfile profile) {
        final String userId = profile.getUserId();

        // Mutable holders populated by whichever callback arrives first.
        final AtomicReference<List<ProfileUiState.Badge>> badgesHolder =
                new AtomicReference<>(Collections.emptyList());
        final AtomicReference<UserStats> statsHolder = new AtomicReference<>(null);
        final AtomicReference<List<WalkReview>> reviewsHolder =
                new AtomicReference<>(Collections.emptyList());

        final AtomicInteger doneCount = new AtomicInteger(0);

        // Called by whichever of the 3 parallel calls completes last.
        Runnable publish = () -> {
            if (doneCount.incrementAndGet() == 3) {
                UserStats stats = statsHolder.get();
                // Prefer API stats if available; fall back to values on UserProfile.
                double distanceKm  = stats != null ? stats.getTotalDistanceKm()   : profile.getTotalDistanceKm();
                int    sessions    = stats != null ? stats.getCompletedSessions()  : profile.getTotalSessions();
                uiState.postValue(new ProfileUiState(
                        false,
                        profile.getFullName(),
                        profile.getAvatarUrl(),
                        false,                   // Gap 2.2: isOnline — no presence system yet; always false
                        (float) profile.getTrustScore(),
                        profile.getTags() != null ? profile.getTags() : Collections.emptyList(),
                        distanceKm,
                        sessions,
                        0,                       // currentStreak — no backend endpoint yet
                        badgesHolder.get(),
                        null,                    // visibilityMode: not yet returned by UserProfile API
                        reviewsHolder.get(),
                        null));
            }
        };

        // ── Badges ────────────────────────────────────────────────────────────
        gamificationRepo.getBadges(userId, new DomainCallback<List<UserBadge>>() {
            @Override
            public void onSuccess(List<UserBadge> userBadges) {
                badgesHolder.set(mapBadges(userBadges));
                publish.run();
            }
            @Override
            public void onError(Exception e) {
                // Badges failure is non-fatal — Milestones card shows empty.
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
                // Stats failure is non-fatal — profile values used as fallback.
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
                // Reviews failure is non-fatal — feed shows empty.
                publish.run();
            }
        });
    }

    /**
     * Converts backend {@link UserBadge} objects to UI badges.
     * Badge names are formatted for display (e.g. "FIRST_WALK" → "First Walk").
     * Icon drawable IDs are intentionally 0 for now — Phase 14 will wire specific
     * drawables per badge name once the asset library is finalised.
     */
    private static List<ProfileUiState.Badge> mapBadges(List<UserBadge> userBadges) {
        if (userBadges == null || userBadges.isEmpty()) return Collections.emptyList();
        List<ProfileUiState.Badge> result = new ArrayList<>(userBadges.size());
        for (UserBadge b : userBadges) {
            result.add(new ProfileUiState.Badge(formatBadgeName(b.getBadgeName()), 0));
        }
        return result;
    }

    /** Converts "FIRST_WALK" → "First Walk". */
    private static String formatBadgeName(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] words = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(' ');
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }

    private static String friendlyError(Exception e) {
        String msg = e.getMessage();
        return (msg != null && !msg.isEmpty()) ? msg : "Failed to load profile";
    }
}
