package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterCustomerRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Past LocalDate dateOfBirth,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9 -]{5,30}", message = "must be 5-30 letters, digits, spaces or hyphens") String nationalId,
        @NotBlank @Size(max = 20) String branchCode
) {

    /** The national id is personal data: keep it out of logs. */
    @Override
    public String toString() {
        return "RegisterCustomerRequest[fullName=" + fullName + ", branchCode=" + branchCode + ", nationalId=***]";
    }
}
