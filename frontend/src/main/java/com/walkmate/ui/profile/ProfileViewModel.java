package com.walkmate.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.R;
import com.walkmate.domain.user.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Profile screen.
 *
 * Owns: name, avatar, online status, trust score, personality tags,
 * milestone stats, and badge list.
 *
 * Data flow:
 *   loadProfile() → posts loading state → builds profile on executor thread
 *   → assembles ProfileUiState → postValue() → ProfileFragment renders.
 *
 * The ViewModel holds zero Context references. R.* resource IDs are
 * compile-time constants (ints), not Context-dependent — safe to use here.
 *
 * NOTE: UserRepository currently has no getMyProfile() endpoint. All user
 * profile fields use hardcoded mock values. Replace with real repo calls
 * when the backend endpoint is available.
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<ProfileUiState> uiState = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SuppressWarnings("FieldCanBeLocal")
    private final UserRepository userRepo;

    public ProfileViewModel(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<ProfileUiState> getUiState() {
        return uiState;
    }

    /**
     * Triggers a profile data load.
     * Safe to call multiple times (e.g., on tab resume). Each call posts a
     * fresh loading state before fetching.
     */
    public void loadProfile() {
        uiState.postValue(ProfileUiState.loading());
        executor.execute(() -> {
            // Simulate 600 ms network delay for the mock
            try { Thread.sleep(600); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            uiState.postValue(buildMockState());
        });
    }

    /**
     * Navigation signals — no business logic in the Fragment lambda.
     * Phase D will connect these to real destinations via a SingleLiveEvent.
     */
    public void onWalkHistoryClicked()  { /* Phase D: emit navigation signal */ }
    public void onMyBadgesClicked()     { /* Phase D: emit navigation signal */ }
    public void onSettingsClicked()     { /* Phase D: emit navigation signal */ }

    @Override
    protected void onCleared() {
        executor.shutdown();
    }

    // ── Mock data builder ─────────────────────────────────────────────────────

    /**
     * Assembles a fully-populated ProfileUiState from hardcoded mock values.
     *
     * Replacement guide (swap each mock field for the real repo call):
     *   name              → userRepo.getMyProfile().fullname
     *   avatarUrl         → userRepo.getMyProfile().avatarUrl
     *   isOnline          → presence service
     *   trustScore        → rating service
     *   personalityTags   → userRepo.getMyProfile().tags
     *   totalDistanceKm   → stats repo
     *   totalSessions     → stats repo
     *   currentStreak     → stats repo
     *   badges            → badge repo mapped to R.drawable / R.string IDs
     */
    private ProfileUiState buildMockState() {
        List<String> tags = Arrays.asList("Chatty", "Dog Friendly");

        List<ProfileUiState.Badge> badges = Arrays.asList(
                new ProfileUiState.Badge(
                        R.string.profile_badge_first_walk,
                        R.drawable.ic_badge_first_walk),
                new ProfileUiState.Badge(
                        R.string.profile_badge_social,
                        R.drawable.ic_badge_social),
                new ProfileUiState.Badge(
                        R.string.profile_badge_streak,
                        R.drawable.ic_badge_streak)
        );

        return new ProfileUiState(
                false,
                "Nguyễn Bảo Duy",
                null,          // avatarUrl — null triggers ic_user placeholder
                true,          // isOnline
                4.9f,          // trustScore
                tags,
                248.0,         // totalDistanceKm
                32,            // totalSessions
                5,             // currentStreak
                badges,
                null);
    }
}
