package org.bank.identity.service.implementation;

import org.bank.common.error.ErrorCode;
import org.bank.common.exception.AccountLockedException;
import org.bank.common.exception.AuthenticationFailedException;
import org.bank.common.exception.BankException;
import org.bank.identity.config.IdentityProperties;
import org.bank.common.dto.request.LoginRequest;
import org.bank.common.dto.request.RefreshTokenRequest;
import org.bank.common.dto.response.LoginResponse;
import org.bank.common.dto.enums.LoginResult;
import org.bank.common.dto.enums.MfaMethod;
import org.bank.identity.model.User;
import org.bank.common.dto.enums.UserStatus;
import org.bank.identity.repository.UserRepository;
import org.bank.identity.security.AccessTokenIssuer;
import org.bank.identity.security.RefreshTokenService;
import org.bank.identity.security.UserAuthorities;
import org.bank.identity.security.UserAuthorityResolver;
import org.bank.identity.security.mfa.MfaChallengeService;
import org.bank.identity.service.ClientContext;
import org.bank.identity.service.LoginAttemptRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PASSWORD = "Dev@Passw0rd!2026";
    private static final int MAX_ATTEMPTS = 3;

    @Mock UserRepository userRepository;
    @Mock UserAuthorityResolver authorityResolver;
    @Mock AccessTokenIssuer accessTokenIssuer;
    @Mock RefreshTokenService refreshTokenService;
    @Mock MfaChallengeService mfaChallengeService;
    @Mock LoginAttemptRecorder attemptRecorder;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final ClientContext context = new ClientContext(null, "junit", "corr-1");

    private AuthServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = new IdentityProperties(
                new IdentityProperties.Lockout(MAX_ATTEMPTS, Duration.ofMinutes(15)),
                new IdentityProperties.Token(Duration.ofMinutes(15), Duration.ofHours(8), Duration.ofDays(7), null, false),
                new IdentityProperties.Mfa(Duration.ofMinutes(5), 5, 6),
                new IdentityProperties.Password(12));
        service = new AuthServiceImpl(userRepository, encoder, authorityResolver, accessTokenIssuer, refreshTokenService,
                mfaChallengeService, attemptRecorder, properties, TransactionOperations.withoutTransaction(), clock);
        user = User.builder().id(UUID.randomUUID()).username("teller1").passwordHash(encoder.encode(PASSWORD)).build();
    }

    private void stubSuccessfulTokenIssue() {
        when(authorityResolver.resolve(user)).thenReturn(new UserAuthorities(Set.of("TELLER"), Set.of("AUTH_LOGIN")));
        when(refreshTokenService.open(user.getId(), "corr-1")).thenReturn(new RefreshTokenService.IssuedRefreshToken(7L, "7.secret"));
        when(accessTokenIssuer.issue(eq(user), any(), eq(7L), anyBoolean()))
                .thenReturn(new AccessTokenIssuer.IssuedAccessToken("jwt", 900));
    }

    @Test
    void loginWithoutMfaReturnsTokensAndRecordsSuccess() {
        when(userRepository.findByUsernameForUpdate("teller1")).thenReturn(Optional.of(user));
        stubSuccessfulTokenIssue();

        LoginResponse response = service.login(new LoginRequest(" Teller1 ", PASSWORD), context);

        assertThat(response.status()).isEqualTo(LoginResponse.Status.AUTHENTICATED);
        assertThat(response.tokens().accessToken()).isEqualTo("jwt");
        assertThat(response.tokens().refreshToken()).isEqualTo("7.secret");
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(attemptRecorder).record(user.getId(), "teller1", LoginResult.SUCCESS, null, context);
    }

    @Test
    void loginWithMfaReturnsChallengeAndNoTokens() {
        user.setMfaEnabled(true);
        when(userRepository.findByUsernameForUpdate("teller1")).thenReturn(Optional.of(user));
        when(mfaChallengeService.issue(user, "corr-1")).thenReturn(
                new MfaChallengeService.IssuedChallenge("9.abc", MfaMethod.EMAIL, OffsetDateTime.now(clock).plusMinutes(5)));

        LoginResponse response = service.login(new LoginRequest("teller1", PASSWORD), context);

        assertThat(response.status()).isEqualTo(LoginResponse.Status.MFA_REQUIRED);
        assertThat(response.tokens()).isNull();
        assertThat(response.mfaChallenge().challengeToken()).isEqualTo("9.abc");
        verify(refreshTokenService, never()).open(any(), any());
    }

    @Test
    void unknownUserGetsGenericErrorAndIsAudited() {
        when(userRepository.findByUsernameForUpdate("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", PASSWORD), context))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(attemptRecorder).record(isNull(), eq("ghost"), eq(LoginResult.FAILED), eq("UNKNOWN_USER"), eq(context));
    }

    @Test
    void wrongPasswordCountsFailureWithoutRevealingLockoutOnTriggeringAttempt() {
        when(userRepository.findByUsernameForUpdate("teller1")).thenReturn(Optional.of(user));

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("teller1", "wrong-password"), context))
                    .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        }

        assertThat(user.getFailedLoginCount()).isEqualTo(MAX_ATTEMPTS);
        assertThat(user.getLockedUntil()).isNotNull();
        verify(attemptRecorder).record(user.getId(), "teller1", LoginResult.FAILED, "INVALID_PASSWORD_LOCKOUT_TRIGGERED", context);
    }

    @Test
    void activeLockoutRejectsEvenTheCorrectPassword() {
        user.registerFailedLogin(OffsetDateTime.now(clock), 1, Duration.ofMinutes(15));
        when(userRepository.findByUsernameForUpdate("teller1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("teller1", PASSWORD), context))
                .isInstanceOfSatisfying(AccountLockedException.class,
                        e -> assertThat(e.getLockedUntil()).isEqualTo(user.getLockedUntil().toInstant()));

        verify(attemptRecorder).record(user.getId(), "teller1", LoginResult.LOCKED, "ACCOUNT_LOCKED", context);
        verify(refreshTokenService, never()).open(any(), any());
    }

    @Test
    void disabledAccountIsOnlyRevealedAfterCorrectPassword() {
        user.disable();
        when(userRepository.findByUsernameForUpdate("teller1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("teller1", "wrong-password"), context))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        assertThatThrownBy(() -> service.login(new LoginRequest("teller1", PASSWORD), context))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED));
    }

    @Test
    void overlongPasswordIsRejectedWithoutBcryptTruncation() {
        when(userRepository.findByUsernameForUpdate("teller1")).thenReturn(Optional.of(user));
        // First 72 bytes equal the real password's bytes padded; the full value must still not match.
        String tooLong = PASSWORD + "x".repeat(80);

        assertThatThrownBy(() -> service.login(new LoginRequest("teller1", tooLong), context))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void refreshRevokesSessionWhenUserWasDisabledMeanwhile() {
        user.disable();
        when(refreshTokenService.rotate("7.secret")).thenReturn(new RefreshTokenService.Rotation(7L, user.getId(), "7.new"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("7.secret"), context))
                .isInstanceOfSatisfying(BankException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));

        verify(refreshTokenService).revokeSession(7L);
        verify(accessTokenIssuer, never()).issue(any(), any(), anyLong(), anyBoolean());
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void refreshIssuesNewAccessTokenWithRotatedRefreshToken() {
        when(refreshTokenService.rotate("7.secret")).thenReturn(new RefreshTokenService.Rotation(7L, user.getId(), "7.new"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(authorityResolver.resolve(user)).thenReturn(new UserAuthorities(Set.of("TELLER"), Set.of()));
        when(accessTokenIssuer.issue(user, new UserAuthorities(Set.of("TELLER"), Set.of()), 7L, false))
                .thenReturn(new AccessTokenIssuer.IssuedAccessToken("jwt2", 900));

        var tokens = service.refresh(new RefreshTokenRequest("7.secret"), context);

        assertThat(tokens.accessToken()).isEqualTo("jwt2");
        assertThat(tokens.refreshToken()).isEqualTo("7.new");
    }
}
