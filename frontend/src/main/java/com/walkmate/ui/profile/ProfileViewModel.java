package com.walkmate.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileErrorCode;
import com.walkmate.domain.profile.ProfileException;
import com.walkmate.domain.profile.ProfileMode;
import com.walkmate.domain.profile.ProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileViewModel extends ViewModel {
    private final ProfileService profileService;
    private final UUID profileOwnerId;
    private final UUID viewerId;
    private final ExecutorService executor;

    private final MutableLiveData<ProfileUiState> _uiState = new MutableLiveData<>();
    public final LiveData<ProfileUiState> uiState = _uiState;

    private final MutableLiveData<ProfileUiEffect> _uiEffect = new MutableLiveData<>();
    public final LiveData<ProfileUiEffect> uiEffect = _uiEffect;

    public ProfileViewModel(ProfileService profileService, UUID profileOwnerId, UUID viewerId) {
        this.profileService = profileService;
        this.profileOwnerId = profileOwnerId;
        this.viewerId = viewerId;
        this.executor = Executors.newSingleThreadExecutor();

        ProfileViewData initialData = ProfileViewData.createInitial(profileOwnerId);
        _uiState.setValue(ProfileUiState.initial(initialData));

        loadProfile();
    }

    public void onEvent(ProfileUiEvent event) {
        if (event instanceof ProfileUiEvent.NameChanged) {
            handleNameChanged(((ProfileUiEvent.NameChanged) event).getName());
        } else if (event instanceof ProfileUiEvent.CityChanged) {
            handleCityChanged(((ProfileUiEvent.CityChanged) event).getCity());
        } else if (event instanceof ProfileUiEvent.BioChanged) {
            handleBioChanged(((ProfileUiEvent.BioChanged) event).getBio());
        } else if (event instanceof ProfileUiEvent.DateOfBirthChanged) {
            handleDateOfBirthChanged(((ProfileUiEvent.DateOfBirthChanged) event).getDateOfBirth());
        } else if (event instanceof ProfileUiEvent.GenderChanged) {
            handleGenderChanged(((ProfileUiEvent.GenderChanged) event).getGender());
        } else if (event instanceof ProfileUiEvent.TagToggled) {
            handleTagToggled(((ProfileUiEvent.TagToggled) event).getTagCode());
        } else if (event instanceof ProfileUiEvent.ProfileModeChanged) {
            handleProfileModeChanged(((ProfileUiEvent.ProfileModeChanged) event).getProfileMode());
        } else if (event instanceof ProfileUiEvent.InfoVisibilityChanged) {
            handleInfoVisibilityChanged(((ProfileUiEvent.InfoVisibilityChanged) event).getMode());
        } else if (event instanceof ProfileUiEvent.SaveClicked) {
            handleSave();
        } else if (event instanceof ProfileUiEvent.BackClicked) {
            _uiEffect.setValue(new ProfileUiEffect.NavigateBack());
        }
    }

    private void loadProfile() {
        ProfileUiState currentState = _uiState.getValue();
        if (currentState == null) {
            return;
        }
        _uiState.setValue(currentState.withLoading(true).clearError());

        executor.execute(() -> {
            try {
                Profile profile = profileService.getProfile(profileOwnerId, viewerId);
                ProfileViewData data = ProfileViewData.fromDomain(profile);
                _uiState.postValue(new ProfileUiState(false, false, data, null, data.canSave()));
            } catch (ProfileException e) {
                ProfileUiState state = _uiState.getValue();
                if (state == null) {
                    return;
                }
                _uiState.postValue(new ProfileUiState(false, false, state.getData(), null, state.getData().canSave()));
            }
        });
    }

    private void handleNameChanged(String name) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        updateData(state.getData().withName(name));
    }

    private void handleCityChanged(String city) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        updateData(state.getData().withCity(city));
    }

    private void handleBioChanged(String bio) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        if (bio != null && bio.length() > 120) {
            return;
        }
        updateData(state.getData().withBio(bio));
    }

    private void handleDateOfBirthChanged(java.time.LocalDate dateOfBirth) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        updateData(state.getData().withDateOfBirth(dateOfBirth));
    }

    private void handleGenderChanged(String gender) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        updateData(state.getData().withGender(gender));
    }

    private void handleTagToggled(String tagCode) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        List<ProfileViewData.TagViewData> updated = new ArrayList<>();
        for (ProfileViewData.TagViewData tag : state.getData().getTags()) {
            if (tag.getCode().equals(tagCode)) {
                updated.add(tag.withSelected(!tag.isSelected()));
            } else {
                updated.add(tag);
            }
        }
        updateData(state.getData().withTags(updated));
    }

    private void handleProfileModeChanged(ProfileMode mode) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        updateData(state.getData().withProfileMode(mode));
    }

    private void handleInfoVisibilityChanged(InfoVisibilityMode mode) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        updateData(state.getData().withInfoVisibilityMode(mode));
    }

    private void handleSave() {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        if (!state.getData().canSave()) {
            _uiEffect.setValue(new ProfileUiEffect.ShowToast("Please fill display name"));
            return;
        }

        _uiState.setValue(state.withSaving(true).clearError());
        Profile domain = mapToDomain(state.getData());

        executor.execute(() -> {
            try {
                Profile saved = profileService.setupProfile(domain);
                ProfileViewData data = ProfileViewData.fromDomain(saved);
                _uiState.postValue(new ProfileUiState(false, false, data, null, data.canSave()));
                _uiEffect.postValue(new ProfileUiEffect.ShowToast("Profile saved"));
                _uiEffect.postValue(new ProfileUiEffect.SaveSuccess());
            } catch (ProfileException e) {
                String error = mapError(e.getErrorCode());
                ProfileUiState current = _uiState.getValue();
                if (current == null) {
                    return;
                }
                _uiState.postValue(new ProfileUiState(false, false, current.getData(), error, current.getData().canSave()));
                _uiEffect.postValue(new ProfileUiEffect.ShowToast(error));
            }
        });
    }

    private Profile mapToDomain(ProfileViewData data) {
        return new Profile(
                data.getUserId(),
                data.getFullName(),
                data.getCity(),
                data.getAvatarUrl(),
                data.getBio(),
                data.getDateOfBirth(),
                data.getGender(),
                data.getProfileMode(),
                data.getInfoVisibilityMode(),
                data.getSelectedDomainTags(),
                data.getEmail(),
                data.getPhone()
        );
    }

    private void updateData(ProfileViewData data) {
        ProfileUiState state = _uiState.getValue();
        if (state == null) {
            return;
        }
        _uiState.setValue(new ProfileUiState(state.isLoading(), state.isSaving(), data, state.getError(), data.canSave()));
    }

    private String mapError(ProfileErrorCode code) {
        switch (code) {
            case PROFILE_PRIVATE:
                return "This profile is private";
            case PROFILE_TAGS_MIN_REQUIRED:
                return "Please select at least 3 tags";
            case PROFILE_INVALID_NAME:
                return "Display name is invalid";
            case NETWORK_ERROR:
                return "Network error. Please try again";
            default:
                return "Cannot save profile right now";
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
