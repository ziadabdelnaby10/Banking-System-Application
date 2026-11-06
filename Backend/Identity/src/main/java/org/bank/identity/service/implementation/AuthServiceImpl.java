package org.bank.identity.service.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bank.common.exception.AccountLockedException;
import org.bank.common.exception.AuthenticationFailedException;
import org.bank.common.exception.BankException;
import org.bank.common.error.ErrorCode;
import org.bank.identity.config.IdentityProperties;
import org.bank.common.dto.request.LoginRequest;
import org.bank.common.dto.request.MfaVerifyRequest;
import org.bank.common.dto.request.RefreshTokenRequest;
import org.bank.common.dto.response.LoginResponse;
import org.bank.common.dto.response.MfaChallengeResponse;
import org.bank.common.dto.response.TokenResponse;
import org.bank.common.dto.enums.LoginResult;
import org.bank.identity.model.User;
import org.bank.common.dto.enums.UserStatus;
import org.bank.identity.repository.UserRepository;
import org.bank.identity.security.AccessTokenIssuer;
import org.bank.identity.security.RefreshTokenService;
import org.bank.identity.security.UserAuthorities;
import org.bank.identity.security.UserAuthorityResolver;
import org.bank.identity.security.mfa.MfaChallengeService;
import org.bank.identity.service.AuthService;
import org.bank.identity.service.ClientContext;
import org.bank.identity.service.LoginAttemptRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Authentication facade.
 * <p>
 * Transaction design: failed logins must still persist the failed-attempt counter, the lockout and the
 * audit row. Throwing from inside a transaction would roll all of that back, so each flow runs in a
 * {@link TransactionOperations} callback that <em>returns</em> a {@link Result} (commit) and the exception
 * is thrown only after the commit. User rows are locked pessimistically to make the counter race-free.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    /**
     * bcrypt silently ignores bytes past 72; longer inputs can never be a valid password here.
     */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthorityResolver authorityResolver;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final MfaChallengeService mfaChallengeService;
    private final LoginAttemptRecorder attemptRecorder;
    private final IdentityProperties properties;
    private final TransactionOperations transactions;
    private final Clock clock;

    /**
     * Result of a transactional step: a value to return, or a failure to throw once the transaction committed.
     */
    private record Result<T>(T value, BankException error) {
        static <T> Result<T> ok(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> reject(BankException error) {
            return new Result<>(null, error);
        }

        T orThrow() {
            if (error != null) {
                throw error;
            }
            return value;
        }
    }

    @Override
    public LoginResponse login(LoginRequest request, ClientContext context) {
        String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
        Result<LoginResponse> result = transactions.execute(status -> attemptLogin(username, request.password(), context));
        return Objects.requireNonNull(result).orThrow();
    }

    private Result<LoginResponse> attemptLogin(String username, String rawPassword, ClientContext context) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        User user = userRepository.findByUsernameForUpdate(username).orElse(null);

        if (user == null) {
            /**
             * Verified against when the username is unknown, so response time does not reveal which users exist.
             */
            String timingEqualisationHash = passwordEncoder.encode(UUID.randomUUID().toString());
            passwordEncoder.matches(rawPassword, timingEqualisationHash);
            attemptRecorder.record(null, username, LoginResult.FAILED, "UNKNOWN_USER", context);
            return Result.reject(AuthenticationFailedException.invalidCredentials());
        }

        // Checked before the password so an active lockout stops brute-forcing completely.
        if (user.isTemporarilyLocked(now)) {
            attemptRecorder.record(user.getId(), username, LoginResult.LOCKED, "ACCOUNT_LOCKED", context);
            return Result.reject(new AccountLockedException(user.getLockedUntil().toInstant()));
        }

        if (!passwordMatches(rawPassword, user.getPasswordHash())) {
            return Result.reject(onFailedPassword(user, username, now, context));
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            // Only revealed after the password was proven correct, so the status of an account
            // cannot be probed by anonymous callers.
            attemptRecorder.record(user.getId(), username, LoginResult.FAILED, "ACCOUNT_" + user.getStatus(), context);
            return Result.reject(user.getStatus() == UserStatus.LOCKED
                    ? new AccountLockedException(null)
                    : new AuthenticationFailedException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled"));
        }

        user.resetFailedLogins();

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            MfaChallengeService.IssuedChallenge challenge = mfaChallengeService.issue(user, context.correlationId());
            attemptRecorder.record(user.getId(), username, LoginResult.MFA_REQUIRED, null, context);
            return Result.ok(LoginResponse.mfaRequired(
                    new MfaChallengeResponse(challenge.token(), challenge.method(), challenge.expiresAt())));
        }

        user.registerSuccessfulLogin(now);
        TokenResponse tokens = issueTokens(user, context, false);
        attemptRecorder.record(user.getId(), username, LoginResult.SUCCESS, null, context);
        return Result.ok(LoginResponse.authenticated(tokens));
    }

    private BankException onFailedPassword(User user, String username, OffsetDateTime now, ClientContext context) {
        IdentityProperties.Lockout lockout = properties.lockout();
        boolean lockedNow = user.registerFailedLogin(now, lockout.maxFailedAttempts(), lockout.lockDuration());
        attemptRecorder.record(user.getId(), username, LoginResult.FAILED,
                lockedNow ? "INVALID_PASSWORD_LOCKOUT_TRIGGERED" : "INVALID_PASSWORD", context);
        if (lockedNow) {
            log.warn("User {} locked until {} after {} failed logins", user.getId(), user.getLockedUntil(),
                    user.getFailedLoginCount());
        }
        // Same answer as for an unknown user; the lock becomes visible on the next attempt.
        return AuthenticationFailedException.invalidCredentials();
    }

    @Override
    public TokenResponse verifyMfa(MfaVerifyRequest request, ClientContext context) {
        Result<TokenResponse> result = transactions.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            MfaChallengeService.Verification verification =
                    mfaChallengeService.verify(request.challengeToken(), request.code());
            if (!verification.verified()) {
                attemptRecorder.record(verification.userId(), null, LoginResult.FAILED, verification.failureReason(), context);
                return Result.reject(AuthenticationFailedException.invalidMfaCode());
            }

            User user = userRepository.findByIdForUpdate(verification.userId()).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE || user.isTemporarilyLocked(now)) {
                attemptRecorder.record(verification.userId(), null, LoginResult.FAILED, "ACCOUNT_NOT_ACTIVE", context);
                return Result.reject(AuthenticationFailedException.invalidMfaCode());
            }

            user.registerSuccessfulLogin(now);
            TokenResponse tokens = issueTokens(user, context, true);
            attemptRecorder.record(user.getId(), user.getUsername(), LoginResult.SUCCESS, null, context);
            return Result.ok(tokens);
        });
        return Objects.requireNonNull(result).orThrow();
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request, ClientContext context) {
        // Rotation commits (or revokes on reuse) in its own transaction before anything else happens.
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());

        Result<TokenResponse> result = transactions.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            User user = userRepository.findById(rotation.userId()).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE || user.isTemporarilyLocked(now)) {
                // Account was disabled/locked after the session started: kill the session.
                refreshTokenService.revokeSession(rotation.sessionId());
                return Result.reject(AuthenticationFailedException.invalidToken());
            }
            UserAuthorities authorities = authorityResolver.resolve(user);
            // A session can only exist for an MFA user after a verified second factor.
            AccessTokenIssuer.IssuedAccessToken access =
                    accessTokenIssuer.issue(user, authorities, rotation.sessionId(), Boolean.TRUE.equals(user.getMfaEnabled()));
            return Result.ok(TokenResponse.bearer(access.value(), access.expiresInSeconds(), rotation.refreshToken()));
        });
        return Objects.requireNonNull(result).orThrow();
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponse issueTokens(User user, ClientContext context, boolean mfaVerified) {
        UserAuthorities authorities = authorityResolver.resolve(user);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.open(user.getId(), context.correlationId());
        AccessTokenIssuer.IssuedAccessToken access = accessTokenIssuer.issue(user, authorities, refresh.sessionId(), mfaVerified);
        return TokenResponse.bearer(access.value(), access.expiresInSeconds(), refresh.value());
    }

    private boolean passwordMatches(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }
}
