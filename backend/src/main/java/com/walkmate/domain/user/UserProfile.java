package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Profile aggregate root — owns editable, non-auth fields of a user.
 *
 * Rich domain invariant: date-of-birth must be at least 13 years in the past.
 */
@Getter
public class UserProfile {

    private static final int MIN_AGE_YEARS = 13;

    private UUID      userId;
    private String    fullName;
    private Gender    gender;
    private LocalDate dateOfBirth;
    private String    avatarUrl;
    private String    bio;

    protected UserProfile() {}

    // ── Rehydration constructor (repository → domain) ─────────────────────────

    public UserProfile(UUID userId, String fullName, Gender gender, LocalDate dateOfBirth,
                       String avatarUrl, String bio) {
        this.userId      = userId;
        this.fullName    = fullName;
        this.gender      = gender;
        this.dateOfBirth = dateOfBirth;
        this.avatarUrl   = avatarUrl;
        this.bio         = bio;
    }

    // ── Creation factories ────────────────────────────────────────────────────

    public static UserProfile createBlank(UUID userId) {
        return new UserProfile(userId, "", null, null, null, null);
    }

    public static UserProfile createForLocal(UUID userId, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new DomainException(UserErrorCode.USER_DISPLAY_NAME_BLANK);
        }
        return new UserProfile(userId, fullName.strip(), null, null, null, null);
    }

    public static UserProfile createForOAuth(UUID userId, String fullName, String avatarUrl) {
        String name = (fullName != null && !fullName.isBlank()) ? fullName.strip() : "";
        return new UserProfile(userId, name, null, null, avatarUrl, null);
    }

    // ── Domain behaviour ──────────────────────────────────────────────────────

    public void update(String fullName, Gender gender, LocalDate dateOfBirth, String bio) {
        if (dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).getYears() < MIN_AGE_YEARS) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA,
                    "User must be at least " + MIN_AGE_YEARS + " years old");
        }
        this.fullName    = fullName != null ? fullName.strip() : this.fullName;
        this.gender      = gender;
        this.dateOfBirth = dateOfBirth;
        this.bio         = bio;
    }

    public void applyAvatar(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
