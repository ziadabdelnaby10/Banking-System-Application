package org.bank.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bank.customer.config.OpenApiConfig;
import org.bank.common.dto.response.KycRecordResponse;
import org.bank.common.dto.request.ReviewKycRequest;
import org.bank.common.dto.request.SubmitKycRequest;
import org.bank.customer.service.KycService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers/{customerId}/kyc")
@Tag(name = "KYC", description = "Know-your-customer evidence and its review (maker-checker). Requires a bearer token.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KycController {

    private final KycService kycService;

    @PostMapping
    @Operation(summary = "Submit KYC evidence",
            description = "Permission: KYC_SUBMIT (maker). One open submission per document type. Returns 201 with a Location header.")
    @PreAuthorize("hasAuthority('KYC_SUBMIT')")
    public ResponseEntity<KycRecordResponse> submit(@PathVariable UUID customerId, @Valid @RequestBody SubmitKycRequest request) {
        KycRecordResponse created = kycService.submit(customerId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{recordId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List a customer's KYC records", description = "Permission: CUSTOMER_VIEW. Newest first.")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public List<KycRecordResponse> list(@PathVariable UUID customerId) {
        return kycService.list(customerId);
    }

    @PostMapping("/{recordId}/review")
    @Operation(summary = "Approve or reject KYC evidence",
            description = "Permission: KYC_REVIEW (checker). The reviewer must differ from the submitter, a reason is mandatory "
                    + "when rejecting, and a decided record can no longer change. Approving an identity document verifies the customer.")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public KycRecordResponse review(@PathVariable UUID customerId, @PathVariable UUID recordId,
                                    @Valid @RequestBody ReviewKycRequest request) {
        return kycService.review(customerId, recordId, request);
    }
}
