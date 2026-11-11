package org.bank.common.dto.request;

import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.BranchStatus;

/** PATCH semantics: fields left null are unchanged. The code is the identity of a branch and cannot change. */
public record UpdateBranchRequest(
        @Size(min = 1, max = 150) String name,
        @Size(max = 100) String region,
        BranchStatus status
) {
}
