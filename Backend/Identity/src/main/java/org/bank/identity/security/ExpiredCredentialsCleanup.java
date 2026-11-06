package org.bank.identity.security;

import lombok.RequiredArgsConstructor;
import org.bank.identity.security.mfa.MfaChallengeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the sessions and mfa_challenges tables small; both are only needed while they are valid. */
@Component
@RequiredArgsConstructor
public class ExpiredCredentialsCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredCredentialsCleanup.class);

    private final RefreshTokenService refreshTokenService;
    private final MfaChallengeService mfaChallengeService;

    @Scheduled(cron = "${bank.identity.cleanup.cron:0 17 * * * *}")
    public void purge() {
        int sessions = refreshTokenService.purgeExpired();
        int challenges = mfaChallengeService.purgeExpired();
        if (sessions > 0 || challenges > 0) {
            log.info("Purged {} expired sessions and {} expired MFA challenges", sessions, challenges);
        }
    }
}
