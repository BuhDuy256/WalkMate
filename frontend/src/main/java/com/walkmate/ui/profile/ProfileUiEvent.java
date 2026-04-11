package com.walkmate.ui.profile;

import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.ProfileAvatarUpload;
import com.walkmate.domain.profile.ProfileMode;

import java.time.LocalDate;

public interface ProfileUiEvent {
    class NameChanged implements ProfileUiEvent {
        private final String name;

        public NameChanged(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    class CityChanged implements ProfileUiEvent {
        private final String city;

        public CityChanged(String city) {
            this.city = city;
        }

        public String getCity() {
            return city;
        }
    }

    class BioChanged implements ProfileUiEvent {
        private final String bio;

        public BioChanged(String bio) {
            this.bio = bio;
        }

        public String getBio() {
            return bio;
        }
    }

    class DateOfBirthChanged implements ProfileUiEvent {
        private final LocalDate dateOfBirth;

        public DateOfBirthChanged(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public LocalDate getDateOfBirth() {
            return dateOfBirth;
        }
    }

    class GenderChanged implements ProfileUiEvent {
        private final String gender;

        public GenderChanged(String gender) {
            this.gender = gender;
        }

        public String getGender() {
            return gender;
        }
    }

    class TagToggled implements ProfileUiEvent {
        private final String tagCode;

        public TagToggled(String tagCode) {
            this.tagCode = tagCode;
        }

        public String getTagCode() {
            return tagCode;
        }
    }

    class ProfileModeChanged implements ProfileUiEvent {
        private final ProfileMode profileMode;

        public ProfileModeChanged(ProfileMode profileMode) {
            this.profileMode = profileMode;
        }

        public ProfileMode getProfileMode() {
            return profileMode;
        }
    }

    class AvatarSelected implements ProfileUiEvent {
        private final ProfileAvatarUpload avatarUpload;

        public AvatarSelected(ProfileAvatarUpload avatarUpload) {
            this.avatarUpload = avatarUpload;
        }

        public ProfileAvatarUpload getAvatarUpload() {
            return avatarUpload;
        }
    }

    class InfoVisibilityChanged implements ProfileUiEvent {
        private final InfoVisibilityMode mode;

        public InfoVisibilityChanged(InfoVisibilityMode mode) {
            this.mode = mode;
        }

        public InfoVisibilityMode getMode() {
            return mode;
        }
    }

    class SaveClicked implements ProfileUiEvent {
    }

    class BackClicked implements ProfileUiEvent {
    }
}
