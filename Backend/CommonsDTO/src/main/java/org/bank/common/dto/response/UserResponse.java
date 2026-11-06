package org.bank.common.dto.response;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/** Public view of a user. Never carries the password hash or lockout internals. */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String status,
        boolean mfaEnabled,
        String branchCode,
        Set<String> roles,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}
