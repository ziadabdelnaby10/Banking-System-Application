package org.bank.common.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** Partial update (PATCH semantics): null fields are left unchanged. */
public record UpdateUserRequest(
        @Email @Size(max = 254) String email,
        @Size(max = 20) String branchCode,
        Boolean mfaEnabled
) implements Serializable {
}
