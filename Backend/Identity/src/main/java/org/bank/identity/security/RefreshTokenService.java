package org.bank.identity.security;

import lombok.RequiredArgsConstructor;
import org.bank.common.exception.AuthenticationFailedException;
import org.bank.common.exception.BankException;
import org.bank.identity.config.IdentityProperties;
import org.bank.identity.model.Session;
import org.bank.identity.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Refresh-token sessions with rotation and reuse detection.
 * <p>
 * Every refresh replaces the secret. Presenting a secret that no longer matches a still-live session
 * means an old token was replayed (stolen or duplicated), so the whole session is revoked.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final SessionRepository sessionRepository;
    private final IdentityProperties properties;
    private final Clock clock;

    public record IssuedRefreshToken(long sessionId, String value) {
    }

    public record Rotation(long sessionId, UUID userId, String refreshToken) {
    }

    @Transactional
    public IssuedRefreshToken open(UUID userId, String correlationId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String secret = OpaqueTokens.newSecret();
        Session session = sessionRepository.save(Session.builder()
                .userId(userId)
                .correlationId(correlationId)
                .refreshTokenHash(OpaqueTokens.sha256Hex(secret))
                .expiresAt(now.plus(properties.token().refreshTtl()))
                .build());
        return new IssuedRefreshToken(session.getId(), OpaqueTokens.format(session.getId(), secret));
    }

    /**
     * noRollbackFor: the revocation performed on reuse detection must be committed even though the
     * call ends with an exception.
     */
    @Transactional(noRollbackFor = BankException.class)
    public Rotation rotate(String rawToken) {
        OpaqueTokens.Parsed parsed = OpaqueTokens.parse(rawToken).orElseThrow(AuthenticationFailedException::invalidToken);
        Session session = sessionRepository.findByIdForUpdate(parsed.id()).orElseThrow(AuthenticationFailedException::invalidToken);
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (!session.isActive(now)) {
            throw AuthenticationFailedException.invalidToken();
        }
        if (!OpaqueTokens.hashMatches(parsed.secret(), session.getRefreshTokenHash())) {
            session.revoke(now);
            log.warn("Refresh token reuse detected, session {} revoked (user {})", session.getId(), session.getUserId());
            throw AuthenticationFailedException.invalidToken();
        }

        String newSecret = OpaqueTokens.newSecret();
        session.setRefreshTokenHash(OpaqueTokens.sha256Hex(newSecret));
        session.setExpiresAt(now.plus(properties.token().refreshTtl()));
        return new Rotation(session.getId(), session.getUserId(), OpaqueTokens.format(session.getId(), newSecret));
    }

    /** Idempotent and silent: logout must not reveal whether a token was valid. */
    @Transactional
    public void revoke(String rawToken) {
        OpaqueTokens.parse(rawToken).flatMap(parsed -> sessionRepository.findByIdForUpdate(parsed.id())
                        .filter(session -> OpaqueTokens.hashMatches(parsed.secret(), session.getRefreshTokenHash())))
                .ifPresent(session -> session.revoke(OffsetDateTime.now(clock)));
    }

    @Transactional
    public void revokeSession(long sessionId) {
        sessionRepository.findByIdForUpdate(sessionId).ifPresent(session -> session.revoke(OffsetDateTime.now(clock)));
    }

    @Transactional
    public int revokeAllForUser(UUID userId) {
        return sessionRepository.revokeAllByUserId(userId, OffsetDateTime.now(clock));
    }

    @Transactional
    public int purgeExpired() {
        return sessionRepository.deleteExpiredBefore(OffsetDateTime.now(clock).minus(properties.token().sessionRetention()));
    }
}
