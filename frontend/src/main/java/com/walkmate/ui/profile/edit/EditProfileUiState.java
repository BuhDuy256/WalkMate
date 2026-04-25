package com.walkmate.ui.profile.edit;

import com.walkmate.domain.user.ProfileTagMaster;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the Edit Profile screen state.
 */
public class EditProfileUiState {

    public final boolean               isLoading;
    public final String                fullName;
    public final String                gender;
    public final String                dateOfBirth;
    public final String                bio;
    /** Tag names currently saved for the user — used to pre-select chips on load. */
    public final List<String>          currentTagNames;
    public final String                avatarUrl;
    public final boolean               saveSuccess;
    public final String                fieldError;
    /** All available master tags; empty until the network call completes. */
    public final List<ProfileTagMaster> masterTags;

    public EditProfileUiState(
            boolean isLoading,
            String fullName,
            String gender,
            String dateOfBirth,
            String bio,
            List<String> currentTagNames,
            String avatarUrl,
            boolean saveSuccess,
            String fieldError,
            List<ProfileTagMaster> masterTags) {
        this.isLoading       = isLoading;
        this.fullName        = fullName;
        this.gender          = gender;
        this.dateOfBirth     = dateOfBirth;
        this.bio             = bio;
        this.currentTagNames = currentTagNames != null ? currentTagNames : Collections.emptyList();
        this.avatarUrl       = avatarUrl;
        this.saveSuccess     = saveSuccess;
        this.fieldError      = fieldError;
        this.masterTags      = masterTags != null ? masterTags : Collections.emptyList();
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static EditProfileUiState loading() {
        return new EditProfileUiState(
                true, null, null, null, null, null, null, false, null, Collections.emptyList());
    }

    public static EditProfileUiState idle() {
        return new EditProfileUiState(
                false, null, null, null, null, null, null, false, null, Collections.emptyList());
    }

    // ── Copy-mutators ─────────────────────────────────────────────────────────

    public EditProfileUiState withLoading(boolean loading) {
        return new EditProfileUiState(
                loading, fullName, gender, dateOfBirth, bio,
                currentTagNames, avatarUrl, saveSuccess, fieldError, masterTags);
    }

    public EditProfileUiState withError(String error) {
        return new EditProfileUiState(
                false, fullName, gender, dateOfBirth, bio,
                currentTagNames, avatarUrl, false, error, masterTags);
    }

    public EditProfileUiState withSaveSuccess() {
        return new EditProfileUiState(
                false, fullName, gender, dateOfBirth, bio,
                currentTagNames, avatarUrl, true, null, masterTags);
    }

    public EditProfileUiState withAvatarUrl(String newAvatarUrl) {
        return new EditProfileUiState(
                false, fullName, gender, dateOfBirth, bio,
                currentTagNames, newAvatarUrl, saveSuccess, null, masterTags);
    }

    public EditProfileUiState withMasterTags(List<ProfileTagMaster> tags) {
        return new EditProfileUiState(
                isLoading, fullName, gender, dateOfBirth, bio,
                currentTagNames, avatarUrl, saveSuccess, fieldError, tags);
    }
}
