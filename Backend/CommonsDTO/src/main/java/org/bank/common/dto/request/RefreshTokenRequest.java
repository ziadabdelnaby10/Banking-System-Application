package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used by both /auth/refresh and /auth/logout. */
public record RefreshTokenRequest(@NotBlank @Size(max = 200) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=***]";
    }
}
