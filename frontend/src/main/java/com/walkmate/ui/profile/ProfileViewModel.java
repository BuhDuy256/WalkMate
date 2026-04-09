package com.walkmate.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.event.AuthEventBus;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.user.VisibilityMode;

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
 *   setVisibility(…) → calls UserRepository.setVisibility() → reloads profile on success.
 *   logoutAll() → calls UserRepository.logoutAll().
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<ProfileUiState> uiState = new MutableLiveData<>();
    private final UserProfileRepository profileRepo;
    private final UserRepository userRepository;

    public ProfileViewModel(UserProfileRepository profileRepo, UserRepository userRepository) {
        this.profileRepo = profileRepo;
        this.userRepository = userRepository;
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
                null,                    // visibilityMode: not yet returned by UserProfile API
                null
        );
    }

    private static String friendlyError(Exception e) {
        String msg = e.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : "Failed to load profile";
    }
}
