package org.bank.identity.security.mfa;

import org.bank.common.dto.enums.MfaMethod;
import org.bank.identity.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * DEV/TEST ONLY: writes the code to the log instead of sending an e-mail. It is registered only for the
 * dev and test profiles, so a production deployment without a real channel fails closed
 * (see {@link MfaDeliveryRegistry}) rather than silently logging one-time codes.
 * The Notification service (ADR-007) will provide the production EMAIL implementation.
 */
@Component
@Profile({"dev", "test"})
public class LoggingEmailMfaCodeDelivery implements MfaCodeDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailMfaCodeDelivery.class);

    @Override
    public MfaMethod method() {
        return MfaMethod.EMAIL;
    }

    @Override
    public void deliver(User user, String code, OffsetDateTime expiresAt) {
        log.warn("[DEV ONLY] MFA code for {} <{}>: {} (valid until {})", user.getUsername(), user.getEmail(), code, expiresAt);
    }
}
