package org.bank.identity.service;

import lombok.RequiredArgsConstructor;
import org.bank.identity.model.LoginAttempt;
import org.bank.common.dto.enums.LoginResult;
import org.bank.identity.repository.LoginAttemptRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Appends to the login audit trail. It deliberately joins the caller's transaction: the auth flow is
 * structured so that rejected logins commit (see AuthServiceImpl), keeping audit and lockout state atomic.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptRecorder {

    private static final int USER_AGENT_MAX = 255;
    private static final int USERNAME_MAX = 100;
    private static final int REASON_MAX = 80;

    private final LoginAttemptRepository repository;

    public void record(UUID userId, String username, LoginResult result, String failureReason, ClientContext context) {
        repository.save(LoginAttempt.builder()
                .userId(userId)
                .username(truncate(username, USERNAME_MAX))
                .ipAddress(context.ipAddress())
                .userAgent(truncate(context.userAgent(), USER_AGENT_MAX))
                .result(result)
                .failureReason(truncate(failureReason, REASON_MAX))
                .correlationId(context.correlationId())
                .build());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
