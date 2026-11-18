package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the freeze / unfreeze / close actions; the reason is mandatory and audited. */
public record ChangeAccountStatusRequest(
        @NotBlank @Size(max = 500) String reason) {
}
