package org.bank.identity.model;

import org.bank.common.dto.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final Duration LOCK = Duration.ofMinutes(15);

    private final User user = User.builder().username("teller1").build();

    @Test
    void locksOnlyWhenMaxAttemptsReached() {
        assertThat(user.registerFailedLogin(NOW, 3, LOCK)).isFalse();
        assertThat(user.registerFailedLogin(NOW, 3, LOCK)).isFalse();
        assertThat(user.isTemporarilyLocked(NOW)).isFalse();

        assertThat(user.registerFailedLogin(NOW, 3, LOCK)).isTrue();

        assertThat(user.isTemporarilyLocked(NOW)).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(LOCK));
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void lockExpiresByItselfAndStartsFreshWindow() {
        user.registerFailedLogin(NOW, 1, LOCK);
        OffsetDateTime later = NOW.plus(LOCK).plusSeconds(1);

        assertThat(user.isTemporarilyLocked(later)).isFalse();
        assertThat(user.registerFailedLogin(later, 3, LOCK)).isFalse();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void successfulLoginClearsCountersAndStampsTime() {
        user.registerFailedLogin(NOW, 1, LOCK);

        user.registerSuccessfulLogin(NOW.plusHours(1));

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void activateClearsAutomaticLockout() {
        user.registerFailedLogin(NOW, 1, LOCK);
        user.lock();

        user.activate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isTemporarilyLocked(NOW)).isFalse();
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    void addRoleIsIdempotentAndRemoveRoleDetaches() {
        Role teller = Role.builder().id(3L).name("TELLER").build();
        User withId = User.builder().id(java.util.UUID.randomUUID()).build();

        withId.addRole(teller);
        withId.addRole(teller);
        assertThat(withId.getUserRoles()).hasSize(1);

        withId.removeRole(3L);
        assertThat(withId.getUserRoles()).isEmpty();
    }
}
