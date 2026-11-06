package org.bank.identity.repository;

import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.UserStatus;

/** Query-string filters for GET /api/v1/users. Branch is overridden for branch-restricted callers. */
public record UserSearchCriteria(
        @Size(max = 100) String q,
        UserStatus status,
        @Size(max = 20) String branchCode,
        @Size(max = 80) String role
) {
}
