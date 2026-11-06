package org.bank.identity.security.mfa;

import org.bank.common.dto.enums.MfaMethod;
import org.bank.identity.model.User;

import java.time.OffsetDateTime;

/** Strategy for delivering a one-time code through one channel (e-mail, SMS, ...). */
public interface MfaCodeDelivery {

    MfaMethod method();

    void deliver(User user, String code, OffsetDateTime expiresAt);
}
