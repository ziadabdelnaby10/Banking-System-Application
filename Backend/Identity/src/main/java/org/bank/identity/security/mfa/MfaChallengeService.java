package org.bank.identity.security.mfa;

import lombok.RequiredArgsConstructor;
import org.bank.identity.config.IdentityProperties;
import org.bank.identity.model.MfaChallenge;
import org.bank.common.dto.enums.MfaMethod;
import org.bank.identity.model.User;
import org.bank.identity.repository.MfaChallengeRepository;
import org.bank.identity.security.OpaqueTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Issues and verifies one-time-code challenges.
 * <p>
 * The client receives {@code <challengeId>.<secret>}; only SHA-256(secret + ":" + code) is stored. A database
 * leak therefore cannot be used to brute-force the short numeric code (the secret is not stored), and
 * challenge ids cannot be enumerated to attack other users' codes.
 * Both methods join the caller's transaction: the auth flow commits attempt counters on failure.
 */
@Service
@RequiredArgsConstructor
public class MfaChallengeService {

    /** Only EMAIL has a delivery channel in this iteration; users have no per-user preferred method yet. */
    static final MfaMethod DEFAULT_METHOD = MfaMethod.EMAIL;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MfaChallengeRepository repository;
    private final MfaDeliveryRegistry deliveryRegistry;
    private final IdentityProperties properties;
    private final Clock clock;

    public record IssuedChallenge(String token, MfaMethod method, OffsetDateTime expiresAt) {
    }

    /** @param failureReason null when verified */
    public record Verification(boolean verified, UUID userId, String failureReason) {
        static Verification success(UUID userId) {
            return new Verification(true, userId, null);
        }

        static Verification failure(UUID userId, String reason) {
            return new Verification(false, userId, reason);
        }
    }

    @Transactional
    public IssuedChallenge issue(User user, String correlationId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plus(properties.mfa().codeTtl());
        String code = newCode(properties.mfa().codeLength());
        String secret = OpaqueTokens.newSecret();

        MfaChallenge challenge = repository.save(MfaChallenge.builder()
                .userId(user.getId())
                .correlationId(correlationId)
                .method(DEFAULT_METHOD)
                .codeHash(hash(secret, code))
                .expiresAt(expiresAt)
                .build());

        deliveryRegistry.forMethod(DEFAULT_METHOD).deliver(user, code, expiresAt);
        return new IssuedChallenge(OpaqueTokens.format(challenge.getId(), secret), DEFAULT_METHOD, expiresAt);
    }

    @Transactional
    public Verification verify(String challengeToken, String code) {
        OpaqueTokens.Parsed parsed = OpaqueTokens.parse(challengeToken).orElse(null);
        if (parsed == null) {
            return Verification.failure(null, "MFA_TOKEN_MALFORMED");
        }
        MfaChallenge challenge = repository.findByIdForUpdate(parsed.id()).orElse(null);
        if (challenge == null) {
            return Verification.failure(null, "MFA_CHALLENGE_UNKNOWN");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!challenge.isUsable(now, properties.mfa().maxAttempts())) {
            return Verification.failure(challenge.getUserId(), "MFA_CHALLENGE_UNUSABLE");
        }
        if (!OpaqueTokens.hashMatches(hashInput(parsed.secret(), code), challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            return Verification.failure(challenge.getUserId(), "MFA_CODE_INVALID");
        }
        challenge.setVerifiedAt(now);
        return Verification.success(challenge.getUserId());
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(OffsetDateTime.now(clock).minus(properties.token().sessionRetention()));
    }

    private static String hashInput(String secret, String code) {
        return secret + ":" + code;
    }

    private static String hash(String secret, String code) {
        return OpaqueTokens.sha256Hex(hashInput(secret, code));
    }

    private static String newCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
}
