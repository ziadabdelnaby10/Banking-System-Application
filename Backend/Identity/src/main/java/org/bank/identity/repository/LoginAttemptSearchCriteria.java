package org.bank.identity.repository;

import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.LoginResult;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoginAttemptSearchCriteria(
        @Size(max = 100) String username,
        UUID userId,
        LoginResult result,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
) {
}
