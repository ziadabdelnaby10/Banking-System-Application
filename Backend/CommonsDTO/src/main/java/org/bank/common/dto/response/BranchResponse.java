package org.bank.common.dto.response;

import org.bank.common.dto.enums.BranchStatus;
import java.time.OffsetDateTime;

public record BranchResponse(
        String code,
        String name,
        String region,
        BranchStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
