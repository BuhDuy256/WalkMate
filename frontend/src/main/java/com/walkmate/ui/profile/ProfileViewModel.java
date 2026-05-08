package com.walkmate.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.event.AuthEventBus;
import com.walkmate.core.util.ErrorMessageResolver;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.domain.report.AdminReport;
import com.walkmate.domain.report.AdminReportRepository;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 *   logoutAll() → calls UserRepository.logoutAll().
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<ProfileUiState> uiState = new MutableLiveData<>();
    private final UserProfileRepository profileRepo;
    private final UserRepository userRepository;
    private final GamificationRepository gamificationRepo;
    private final ReviewRepository       reviewRepo;
    private final SessionManager         sessionManager;
    private final AdminReportRepository  adminReportRepo;

    public ProfileViewModel(UserProfileRepository profileRepo,
                            UserRepository userRepository,
                            GamificationRepository gamificationRepo,
                            ReviewRepository reviewRepo,
                            SessionManager sessionManager,
                            AdminReportRepository adminReportRepo) {
        this.profileRepo      = profileRepo;
        this.userRepository   = userRepository;
        this.gamificationRepo = gamificationRepo;
        this.reviewRepo       = reviewRepo;
        this.sessionManager   = sessionManager;
        this.adminReportRepo  = adminReportRepo;
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
                            String bio, List<String> tags) {
        uiState.postValue(ProfileUiState.loading());

        profileRepo.updateProfile(fullName, gender, dateOfBirth, bio, tags,
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

    private final MutableLiveData<Boolean> navigateToBadgesEvent = new MutableLiveData<>();

    public LiveData<Boolean> getNavigateToBadgesEvent() {
        return navigateToBadgesEvent;
    }

    public void consumeNavigateToBadges() {
        navigateToBadgesEvent.setValue(false);
    }

    public void onMyBadgesClicked() {
        navigateToBadgesEvent.postValue(true);
    }

    public void onSettingsClicked()    { /* Phase D: emit navigation signal */ }

    private final MutableLiveData<Boolean> navigateToAdminPanelEvent = new MutableLiveData<>();

    public LiveData<Boolean> getNavigateToAdminPanelEvent() {
        return navigateToAdminPanelEvent;
    }

    public void consumeNavigateToAdminPanel() {
        navigateToAdminPanelEvent.setValue(false);
    }

    public void onOpenAdminPanelClicked() {
        navigateToAdminPanelEvent.postValue(true);
    }

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

    private final MutableLiveData<Boolean> navigateToWalkActivityEvent = new MutableLiveData<>();

    public LiveData<Boolean> getNavigateToWalkActivityEvent() {
        return navigateToWalkActivityEvent;
    }

    public void consumeNavigateToWalkActivity() {
        navigateToWalkActivityEvent.setValue(false);
    }

    public void onWalkActivityClicked() {
        navigateToWalkActivityEvent.postValue(true);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * After the base profile is available, fires badges, stats, and reviews in
     * parallel. AtomicInteger(3) acts as a barrier: the last call to return
     * publishes the merged final state.
     */
    private void loadSupplementalData(UserProfile profile) {
        final String userId = profile.getUserId();
        final boolean isAdmin = sessionManager.isAdmin();

        final AtomicReference<List<ProfileUiState.Badge>> badgesHolder =
                new AtomicReference<>(Collections.emptyList());
        final AtomicReference<UserStats> statsHolder = new AtomicReference<>(null);
        final AtomicReference<List<WalkReview>> reviewsHolder =
                new AtomicReference<>(Collections.emptyList());
        final AtomicReference<Integer> pendingCountHolder = new AtomicReference<>(0);

        final int totalCalls = isAdmin ? 4 : 3;
        final AtomicInteger doneCount = new AtomicInteger(0);

        Runnable publish = () -> {
            if (doneCount.incrementAndGet() == totalCalls) {
                UserStats stats = statsHolder.get();
                double distanceKm = stats != null ? stats.getTotalDistanceKm()  : profile.getTotalDistanceKm();
                int    sessions   = stats != null ? stats.getCompletedSessions(): profile.getTotalSessions();
                uiState.postValue(new ProfileUiState(
                        false,
                        profile.getFullName(),
                        profile.getAvatarUrl(),
                        (float) profile.getTrustScore(),
                        profile.getTags() != null ? profile.getTags() : Collections.emptyList(),
                        distanceKm,
                        sessions,
                        badgesHolder.get(),
                        reviewsHolder.get(),
                        null,
                        isAdmin,
                        pendingCountHolder.get()));
            }
        };

        // ── Badges ────────────────────────────────────────────────────────────
        gamificationRepo.getBadges(userId, new DomainCallback<List<UserBadge>>() {
            @Override public void onSuccess(List<UserBadge> userBadges) {
                badgesHolder.set(toBadgeUiList(userBadges));
                publish.run();
            }
            @Override public void onError(Exception e) { publish.run(); }
        });

        // ── Stats ─────────────────────────────────────────────────────────────
        gamificationRepo.getStats(userId, new DomainCallback<UserStats>() {
            @Override public void onSuccess(UserStats stats) {
                statsHolder.set(stats);
                publish.run();
            }
            @Override public void onError(Exception e) { publish.run(); }
        });

        // ── Reviews ───────────────────────────────────────────────────────────
        reviewRepo.getReviewsForUser(userId, new DomainCallback<List<WalkReview>>() {
            @Override public void onSuccess(List<WalkReview> reviews) {
                reviewsHolder.set(reviews != null ? reviews : Collections.emptyList());
                publish.run();
            }
            @Override public void onError(Exception e) { publish.run(); }
        });

        // ── Admin pending count (admin only) ──────────────────────────────────
        if (isAdmin) {
            adminReportRepo.getReportsByStatus("OPEN", new DomainCallback<List<AdminReport>>() {
                @Override public void onSuccess(List<AdminReport> reports) {
                    pendingCountHolder.set(reports != null ? reports.size() : 0);
                    publish.run();
                }
                @Override public void onError(Exception e) { publish.run(); }
            });
        }
    }

    private static List<ProfileUiState.Badge> toBadgeUiList(List<UserBadge> userBadges) {
        if (userBadges == null || userBadges.isEmpty()) return Collections.emptyList();
        List<ProfileUiState.Badge> result = new ArrayList<>(userBadges.size());
        for (UserBadge b : userBadges) {
            result.add(new ProfileUiState.Badge(b.getDisplayName(), b.getRarity()));
        }
        return result;
    }

    private static String friendlyError(Exception e) {
        return ErrorMessageResolver.resolve(e.getMessage());
    }
}
