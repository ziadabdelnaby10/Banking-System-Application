package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.bank.common.validation.StrongPassword;

import java.io.Serializable;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @StrongPassword String newPassword
) implements Serializable {

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
