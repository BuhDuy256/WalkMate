package com.walkmate.ui.profile.edit;

import java.util.List;

/**
 * Immutable snapshot of the Edit Profile screen state.
 *
 * Rule: no setters. EditProfileViewModel calls postValue(new EditProfileUiState(...))
 * to deliver each new state to the Fragment.
 */
public class EditProfileUiState {

    public final boolean      isLoading;
    public final String       fullName;
    public final String       gender;
    public final String       dateOfBirth;   // "YYYY-MM-DD", nullable
    public final String       bio;
    public final int          searchRadius;  // metres
    public final List<String> tags;
    public final String       avatarUrl;     // nullable
    public final boolean      saveSuccess;
    /** Validation or server error to show inline; null when no error. */
    public final String       fieldError;

    public EditProfileUiState(
            boolean isLoading,
            String fullName,
            String gender,
            String dateOfBirth,
            String bio,
            int searchRadius,
            List<String> tags,
            String avatarUrl,
            boolean saveSuccess,
            String fieldError) {
        this.isLoading    = isLoading;
        this.fullName     = fullName;
        this.gender       = gender;
        this.dateOfBirth  = dateOfBirth;
        this.bio          = bio;
        this.searchRadius = searchRadius;
        this.tags         = tags;
        this.avatarUrl    = avatarUrl;
        this.saveSuccess  = saveSuccess;
        this.fieldError   = fieldError;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static EditProfileUiState loading() {
        return new EditProfileUiState(
                true, null, null, null, null, 0, null, null, false, null);
    }

    public static EditProfileUiState idle() {
        return new EditProfileUiState(
                false, null, null, null, null, 0, null, null, false, null);
    }

    // ── Copy-mutators ─────────────────────────────────────────────────────────

    public EditProfileUiState withLoading(boolean loading) {
        return new EditProfileUiState(
                loading, fullName, gender, dateOfBirth, bio,
                searchRadius, tags, avatarUrl, saveSuccess, fieldError);
    }

    public EditProfileUiState withError(String error) {
        return new EditProfileUiState(
                false, fullName, gender, dateOfBirth, bio,
                searchRadius, tags, avatarUrl, false, error);
    }

    public EditProfileUiState withSaveSuccess() {
        return new EditProfileUiState(
                false, fullName, gender, dateOfBirth, bio,
                searchRadius, tags, avatarUrl, true, null);
    }

    public EditProfileUiState withAvatarUrl(String newAvatarUrl) {
        return new EditProfileUiState(
                false, fullName, gender, dateOfBirth, bio,
                searchRadius, tags, newAvatarUrl, saveSuccess, null);
    }
}
