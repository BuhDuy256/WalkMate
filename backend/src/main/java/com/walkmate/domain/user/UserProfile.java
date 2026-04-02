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
 * search_radius must be 0 < r <= 50 000 metres.
 */
@Getter
public class UserProfile {

    private static final int MIN_AGE_YEARS   = 13;
    private static final int MAX_RADIUS_M    = 50_000;

    private UUID      userId;
    private String    fullName;
    private Gender    gender;
    private LocalDate dateOfBirth;
    private String    avatarUrl;
    private String    bio;
    private int       searchRadius;  // metres

    protected UserProfile() {}

    // ── Rehydration constructor (repository → domain) ─────────────────────────

    public UserProfile(UUID userId, String fullName, Gender gender, LocalDate dateOfBirth,
                       String avatarUrl, String bio, int searchRadius) {
        this.userId       = userId;
        this.fullName     = fullName;
        this.gender       = gender;
        this.dateOfBirth  = dateOfBirth;
        this.avatarUrl    = avatarUrl;
        this.bio          = bio;
        this.searchRadius = searchRadius;
    }

    // ── Creation factory ──────────────────────────────────────────────────────

    /** Creates a blank profile for a newly registered user. */
    public static UserProfile createBlank(UUID userId) {
        return new UserProfile(userId, "", null, null, null, null, 5000);
    }

    // ── Domain behaviour ──────────────────────────────────────────────────────

    public void update(String fullName, Gender gender, LocalDate dateOfBirth,
                       String bio, int searchRadius) {
        if (dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).getYears() < MIN_AGE_YEARS) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA,
                    "User must be at least " + MIN_AGE_YEARS + " years old");
        }
        if (searchRadius <= 0 || searchRadius > MAX_RADIUS_M) {
            throw new DomainException(UserErrorCode.INVALID_USER_DATA,
                    "searchRadius must be between 1 and " + MAX_RADIUS_M + " metres");
        }
        this.fullName     = fullName != null ? fullName.strip() : this.fullName;
        this.gender       = gender;
        this.dateOfBirth  = dateOfBirth;
        this.bio          = bio;
        this.searchRadius = searchRadius;
    }

    public void applyAvatar(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
