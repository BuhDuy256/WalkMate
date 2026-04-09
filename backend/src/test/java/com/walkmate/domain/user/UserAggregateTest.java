package com.walkmate.domain.user;

import com.walkmate.domain.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link User} aggregate.
 * Covers: register(), registerWithPhone(), validateCredentials() purity, setVisibilityMode() idempotency.
 */
class UserAggregateTest {

    // ── register() ────────────────────────────────────────────────────────────

    @Test
    void register_validEmail_createsLocalUserWithNormalizedEmail() {
        User user = User.register("Test@Example.COM", "hashed-pw");

        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getVisibilityMode()).isEqualTo(VisibilityMode.PUBLIC);
        assertThat(user.getLastLoginAt()).isNull();
    }

    @Test
    void register_invalidEmailFormat_throwsUserInvalidEmailFormat() {
        assertThatThrownBy(() -> User.register("not-an-email", "hashed"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_INVALID_EMAIL_FORMAT));
    }

    @Test
    void register_missingAtSign_throwsUserInvalidEmailFormat() {
        assertThatThrownBy(() -> User.register("nodomain.com", "hashed"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_INVALID_EMAIL_FORMAT));
    }

    // ── registerWithPhone() ───────────────────────────────────────────────────

    @Test
    void registerWithPhone_validE164Number_createsPhoneUser() {
        Phone phone = new Phone("+84901234567");
        User user = User.registerWithPhone(phone);

        assertThat(user.getPhone()).isEqualTo("+84901234567");
        assertThat(user.getProvider()).isEqualTo(AuthProvider.PHONE);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getVisibilityMode()).isEqualTo(VisibilityMode.PUBLIC);
    }

    @Test
    void registerWithPhone_invalidFormat_throwsDomainException() {
        assertThatThrownBy(() -> new Phone("0901234567")) // missing +country code
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.INVALID_USER_DATA));
    }

    // ── validateCredentials() — purity contract ───────────────────────────────

    @Test
    void validateCredentials_correctPassword_doesNotMutateLastLoginAt() {
        User user = activeLocalUser();
        Instant lastLoginBefore = user.getLastLoginAt(); // null for new user

        user.validateCredentials("correct-password", (raw, hash) -> true);

        // Pure validation must NOT touch lastLoginAt — that is recordLogin()'s job
        assertThat(user.getLastLoginAt()).isEqualTo(lastLoginBefore);
    }

    @Test
    void validateCredentials_incorrectPassword_throwsUserInvalidCredentials() {
        User user = activeLocalUser();

        assertThatThrownBy(() -> user.validateCredentials("wrong-password", (raw, hash) -> false))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_INVALID_CREDENTIALS));
    }

    @Test
    void validateCredentials_suspendedAccount_throwsUserAccountSuspended() {
        User user = suspendedUser();

        assertThatThrownBy(() -> user.validateCredentials("any-password", (raw, hash) -> true))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_ACCOUNT_SUSPENDED));
    }

    @Test
    void validateCredentials_bannedAccount_throwsUserAccountSuspended() {
        User user = bannedUser();

        assertThatThrownBy(() -> user.validateCredentials("any-password", (raw, hash) -> true))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_ACCOUNT_SUSPENDED));
    }

    // ── setVisibilityMode() — idempotency guards ──────────────────────────────

    @Test
    void setVisibilityMode_publicToPrivate_updatesMode() {
        User user = activeLocalUser(); // starts PUBLIC

        user.setVisibilityMode(VisibilityMode.PRIVATE);

        assertThat(user.getVisibilityMode()).isEqualTo(VisibilityMode.PRIVATE);
    }

    @Test
    void setVisibilityMode_privateToPublic_updatesMode() {
        User user = privateUser();

        user.setVisibilityMode(VisibilityMode.PUBLIC);

        assertThat(user.getVisibilityMode()).isEqualTo(VisibilityMode.PUBLIC);
    }

    @Test
    void setVisibilityMode_alreadyPublic_throwsUserAlreadyPublic() {
        User user = activeLocalUser(); // PUBLIC

        assertThatThrownBy(() -> user.setVisibilityMode(VisibilityMode.PUBLIC))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_ALREADY_PUBLIC));
    }

    @Test
    void setVisibilityMode_alreadyPrivate_throwsUserAlreadyPrivate() {
        User user = privateUser();

        assertThatThrownBy(() -> user.setVisibilityMode(VisibilityMode.PRIVATE))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_ALREADY_PRIVATE));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User activeLocalUser() {
        return new User(UUID.randomUUID(), "user@example.com", null,
                AuthProvider.LOCAL, AccountStatus.ACTIVE, VisibilityMode.PUBLIC,
                "hashed-pw", null, Instant.now(), null,
                0, 0, 0.0, 0, null);
    }

    private User suspendedUser() {
        return new User(UUID.randomUUID(), "user@example.com", null,
                AuthProvider.LOCAL, AccountStatus.SUSPENDED, VisibilityMode.PUBLIC,
                "hashed-pw", null, Instant.now(), null,
                0, 0, 0.0, 0, null);
    }

    private User bannedUser() {
        return new User(UUID.randomUUID(), "user@example.com", null,
                AuthProvider.LOCAL, AccountStatus.BANNED, VisibilityMode.PUBLIC,
                "hashed-pw", null, Instant.now(), null,
                0, 0, 0.0, 0, null);
    }

    private User privateUser() {
        return new User(UUID.randomUUID(), "user@example.com", null,
                AuthProvider.LOCAL, AccountStatus.ACTIVE, VisibilityMode.PRIVATE,
                "hashed-pw", null, Instant.now(), null,
                0, 0, 0.0, 0, null);
    }
}
