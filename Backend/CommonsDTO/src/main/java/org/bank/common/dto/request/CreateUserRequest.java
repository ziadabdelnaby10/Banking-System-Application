package org.bank.common.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.bank.common.validation.StrongPassword;

import java.io.Serializable;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
        @NotBlank @Email @Size(max = 254)
        String email,
        @NotBlank @StrongPassword
        String password,
        @Size(max = 20)
        String branchCode,
        @Size(max = 10)
        Set<Long> roleIds
) implements Serializable {

    /** Never print the password, even by accident in logs. */
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", email=" + email + ", branchCode=" + branchCode
                + ", roleIds=" + roleIds + ", password=***]";
    }
}