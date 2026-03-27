package com.walkmate.ui.profile;

public class ProfileUiState {
    private final boolean loading;
    private final boolean saving;
    private final ProfileViewData data;
    private final String error;
    private final boolean saveEnabled;

    public ProfileUiState(boolean loading, boolean saving, ProfileViewData data, String error, boolean saveEnabled) {
        this.loading = loading;
        this.saving = saving;
        this.data = data;
        this.error = error;
        this.saveEnabled = saveEnabled;
    }

    public static ProfileUiState initial(ProfileViewData data) {
        return new ProfileUiState(true, false, data, null, data.canSave());
    }

    public ProfileUiState withData(ProfileViewData newData) {
        return new ProfileUiState(loading, saving, newData, error, newData.canSave());
    }

    public ProfileUiState withLoading(boolean isLoading) {
        return new ProfileUiState(isLoading, saving, data, error, saveEnabled);
    }

    public ProfileUiState withSaving(boolean isSaving) {
        return new ProfileUiState(loading, isSaving, data, error, saveEnabled);
    }

    public ProfileUiState withError(String newError) {
        return new ProfileUiState(false, false, data, newError, saveEnabled);
    }

    public ProfileUiState clearError() {
        return new ProfileUiState(loading, saving, data, null, saveEnabled);
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isSaving() {
        return saving;
    }

    public ProfileViewData getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public boolean isSaveEnabled() {
        return saveEnabled;
    }
}
