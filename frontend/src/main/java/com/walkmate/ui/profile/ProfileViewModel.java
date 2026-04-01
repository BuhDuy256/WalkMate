package com.walkmate.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;

import java.util.Collections;
import java.util.List;

/**
 * ViewModel for the Profile screen.
 *
 * Data flow:
 *   loadProfile() → posts loading state → calls UserProfileRepository.getMyProfile()
 *   → maps UserProfile → ProfileUiState → postValue() → ProfileFragment renders.
 *
 * Edit flow:
 *   saveProfile(…) → calls updateProfile() → reloads profile on success.
 *   uploadAvatar(…) → calls uploadAvatar() → reloads profile on success.
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<ProfileUiState> uiState = new MutableLiveData<>();
    private final UserProfileRepository profileRepo;

    public ProfileViewModel(UserProfileRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<ProfileUiState> getUiState() {
        return uiState;
    }

    /**
     * Loads the authenticated user's profile from the backend.
     * Safe to call multiple times (e.g. on tab resume).
     */
    public void loadProfile() {
        uiState.postValue(ProfileUiState.loading());

        profileRepo.getMyProfile(new com.walkmate.domain.shared.DomainCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                uiState.postValue(toUiState(profile));
            }

            @Override
            public void onError(Exception e) {
                uiState.postValue(ProfileUiState.error(friendlyError(e)));
            }
        });
    }

    /**
     * Persists profile changes to the backend then reloads the profile.
     * Called from a future Edit Profile screen.
     */
    public void saveProfile(String fullName, String gender, String dateOfBirth,
                            String bio, int searchRadius, List<String> tags) {
        uiState.postValue(ProfileUiState.loading());

        profileRepo.updateProfile(fullName, gender, dateOfBirth, bio, searchRadius, tags,
                new com.walkmate.domain.shared.DomainCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        uiState.postValue(toUiState(profile));
                    }

                    @Override
                    public void onError(Exception e) {
                        uiState.postValue(ProfileUiState.error(friendlyError(e)));
                    }
                });
    }

    /**
     * Uploads a new avatar image then reloads the profile.
     *
     * @param imageBytes raw JPEG/PNG bytes from the image picker
     * @param filename   original filename (used for MIME detection on the server)
     * @param mimeType   e.g. "image/jpeg"
     */
    public void uploadAvatar(byte[] imageBytes, String filename, String mimeType) {
        profileRepo.uploadAvatar(imageBytes, filename, mimeType,
                new com.walkmate.domain.shared.DomainCallback<String>() {
                    @Override
                    public void onSuccess(String avatarUrl) {
                        // Reload full profile so the avatar change is reflected
                        loadProfile();
                    }

                    @Override
                    public void onError(Exception e) {
                        uiState.postValue(ProfileUiState.error(friendlyError(e)));
                    }
                });
    }

    /** Navigation signals — wired to real destinations in a future nav phase. */
    public void onWalkHistoryClicked()  { /* Phase D: emit navigation signal */ }
    public void onMyBadgesClicked()     { /* Phase D: emit navigation signal */ }
    public void onSettingsClicked()     { /* Phase D: emit navigation signal */ }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private static ProfileUiState toUiState(UserProfile p) {
        List<String> tags = p.getTags() != null ? p.getTags() : Collections.emptyList();

        return new ProfileUiState(
                false,
                p.getFullName(),
                p.getAvatarUrl(),
                true,                    // isOnline: presence service not yet available
                (float) p.getTrustScore(),
                tags,
                p.getTotalDistanceKm(),
                p.getTotalSessions(),
                0,                       // currentStreak: requires session analytics (future phase)
                Collections.emptyList(), // badges: loaded separately via GamificationRepository
                null
        );
    }

    private static String friendlyError(Exception e) {
        String msg = e.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : "Failed to load profile";
    }
}
