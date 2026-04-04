package com.walkmate.ui.profile;

import com.walkmate.domain.profile.InfoVisibilityMode;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileMode;
import com.walkmate.domain.profile.ProfileTag;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProfileViewData {
    private final UUID userId;
    private final String fullName;
    private final String city;
    private final String avatarUrl;
    private final String bio;
    private final LocalDate dateOfBirth;
    private final String gender;
    private final ProfileMode profileMode;
    private final InfoVisibilityMode infoVisibilityMode;
    private final List<TagViewData> tags;
    private final String email;
    private final String phone;

    public ProfileViewData(
            UUID userId,
            String fullName,
            String city,
            String avatarUrl,
            String bio,
            LocalDate dateOfBirth,
            String gender,
            ProfileMode profileMode,
            InfoVisibilityMode infoVisibilityMode,
            List<TagViewData> tags,
            String email,
            String phone
    ) {
        this.userId = userId;
        this.fullName = fullName;
        this.city = city;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.profileMode = profileMode;
        this.infoVisibilityMode = infoVisibilityMode;
        this.tags = tags;
        this.email = email;
        this.phone = phone;
    }

    public static ProfileViewData createInitial(UUID userId) {
        List<String> defaultSelectedCodes = Arrays.asList(
                "PET_WALKING",
                "NATURE_LOVER",
                "QUIET_WALK",
                "MORNING_BIRD"
        );
        List<TagViewData> defaultTags = new ArrayList<>();
        for (ProfileTag tag : ProfileTag.values()) {
            defaultTags.add(new TagViewData(
                    tag.name(),
                    tag.getCategory(),
                    tag.getDisplayName(),
                    defaultSelectedCodes.contains(tag.name())
            ));
        }
        return new ProfileViewData(
                userId,
                "",
                "",
                "",
                "",
                null,
                null,
                ProfileMode.PUBLIC,
                InfoVisibilityMode.PRIVATE,
                defaultTags,
                "",
                ""
        );
    }

    public static ProfileViewData fromDomain(Profile profile) {
        List<TagViewData> defaultTags = createInitial(profile.getUserId()).getTags();
        List<String> selectedTagCodes = profile.getTags().stream()
                .map(ProfileTag::name)
                .collect(Collectors.toList());

        List<TagViewData> mappedTags = defaultTags.stream()
                .map(tag -> tag.withSelected(selectedTagCodes.contains(tag.getCode())))
                .collect(Collectors.toList());

        return new ProfileViewData(
                profile.getUserId(),
                profile.getFullName(),
                profile.getCity(),
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getDateOfBirth(),
                profile.getGender(),
                profile.getProfileMode(),
                profile.getInfoVisibilityMode(),
                mappedTags,
                profile.getEmail(),
                profile.getPhone()
        );
    }

    public ProfileViewData withName(String name) {
        return new ProfileViewData(
                userId,
                name,
                city,
                avatarUrl,
                bio,
                dateOfBirth,
                gender,
                profileMode,
                infoVisibilityMode,
                tags,
                email,
                phone
        );
    }

    public ProfileViewData withCity(String newCity) {
        return new ProfileViewData(
                userId,
                fullName,
                newCity,
                avatarUrl,
                bio,
                dateOfBirth,
                gender,
                profileMode,
                infoVisibilityMode,
                tags,
                email,
                phone
        );
    }

    public ProfileViewData withBio(String newBio) {
        return new ProfileViewData(
                userId,
                fullName,
                city,
                avatarUrl,
                newBio,
                dateOfBirth,
                gender,
                profileMode,
                infoVisibilityMode,
                tags,
                email,
                phone
        );
    }

    public ProfileViewData withProfileMode(ProfileMode mode) {
        return new ProfileViewData(
                userId,
                fullName,
                city,
                avatarUrl,
                bio,
                dateOfBirth,
                gender,
                mode,
                infoVisibilityMode,
                tags,
                email,
                phone
        );
    }

    public ProfileViewData withInfoVisibilityMode(InfoVisibilityMode mode) {
        return new ProfileViewData(
                userId,
                fullName,
                city,
                avatarUrl,
                bio,
                dateOfBirth,
                gender,
                profileMode,
                mode,
                tags,
                email,
                phone
        );
    }

    public ProfileViewData withTags(List<TagViewData> newTags) {
        return new ProfileViewData(
                userId,
                fullName,
                city,
                avatarUrl,
                bio,
                dateOfBirth,
                gender,
                profileMode,
                infoVisibilityMode,
                newTags,
                email,
                phone
        );
    }

    public ProfileViewData withDateOfBirth(LocalDate newDateOfBirth) {
        return new ProfileViewData(
                userId,
                fullName,
                city,
                avatarUrl,
                bio,
                newDateOfBirth,
                gender,
                profileMode,
                infoVisibilityMode,
                tags,
                email,
                phone
        );
    }

    public ProfileViewData withGender(String newGender) {
        return new ProfileViewData(
                userId,
                fullName,
                city,
                avatarUrl,
                bio,
                dateOfBirth,
                newGender,
                profileMode,
                infoVisibilityMode,
                tags,
                email,
                phone
        );
    }

    public boolean canSave() {
        return fullName != null && !fullName.trim().isEmpty();
    }

    public int getSelectedCount() {
        int count = 0;
        for (TagViewData tag : tags) {
            if (tag.isSelected()) {
                count++;
            }
        }
        return count;
    }

    public List<TagViewData> getTagsByCategory(String category) {
        return tags.stream()
                .filter(tag -> tag.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    public List<ProfileTag> getSelectedDomainTags() {
        return tags.stream()
                .filter(TagViewData::isSelected)
                .map(TagViewData::getCode)
                .map(ProfileTag::fromCode)
                .collect(Collectors.toList());
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCity() {
        return city;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public ProfileMode getProfileMode() {
        return profileMode;
    }

    public InfoVisibilityMode getInfoVisibilityMode() {
        return infoVisibilityMode;
    }

    public List<TagViewData> getTags() {
        return tags;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public static class TagViewData {
        private final String code;
        private final String category;
        private final String label;
        private final boolean selected;

        public TagViewData(String code, String category, String label, boolean selected) {
            this.code = code;
            this.category = category;
            this.label = label;
            this.selected = selected;
        }

        public TagViewData withSelected(boolean isSelected) {
            return new TagViewData(code, category, label, isSelected);
        }

        public String getCode() {
            return code;
        }

        public String getCategory() {
            return category;
        }

        public String getLabel() {
            return label;
        }

        public boolean isSelected() {
            return selected;
        }
    }
}
