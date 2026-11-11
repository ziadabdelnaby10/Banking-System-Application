package org.bank.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bank.common.dto.response.PageResponse;
import org.bank.customer.config.OpenApiConfig;
import org.bank.common.dto.request.ChangeCustomerStatusRequest;
import org.bank.common.dto.response.CustomerResponse;
import org.bank.common.dto.response.CustomerSummaryResponse;
import org.bank.common.dto.request.RegisterCustomerRequest;
import org.bank.common.dto.request.UpdateCustomerRequest;
import org.bank.customer.repository.CustomerSearchCriteria;
import org.bank.customer.service.CustomerService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer profiles. Requires a bearer token; the permission needed is listed per operation. "
        + "Data is scoped to the caller's branch unless the caller holds a cross-branch role.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CustomerController {

    private static final Set<String> SORTABLE = Set.of("fullName", "customerNo", "createdAt", "status", "kycStatus", "riskLevel", "branchCode");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final CustomerService customerService;

    @PostMapping
    @Operation(summary = "Register a customer",
            description = "Permission: CUSTOMER_CREATE, and the branch must be one the caller may access and be active. "
                    + "The national id must be unique; it is stored as a keyed hash and only returned masked. "
                    + "Returns 201 with a Location header.")
    @PreAuthorize("hasAuthority('CUSTOMER_CREATE') and @branchAccess.canAccess(#request.branchCode())")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        CustomerResponse created = customerService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Search customers",
            description = "Permission: CUSTOMER_VIEW. 'q' matches the start of the name or customer number. Branch-restricted callers "
                    + "only see their own branch. Page size is capped at 100. "
                    + "Sortable fields: fullName, customerNo, createdAt, status, kycStatus, riskLevel, branchCode.")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public PageResponse<CustomerSummaryResponse> search(@ParameterObject @Valid CustomerSearchCriteria criteria,
                                                        @ParameterObject Pageable pageable) {
        return customerService.search(criteria, SafePageable.restrict(pageable, SORTABLE, DEFAULT_SORT));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by id",
            description = "Permission: CUSTOMER_VIEW. Includes the KYC records. Customers outside the caller's branch are reported as not found.")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a customer",
            description = "Permission: CUSTOMER_UPDATE (plus CUSTOMER_STATUS_CHANGE to change the risk level). "
                    + "Partial update: fields left null are unchanged. Closed customers cannot be modified.")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a customer's status",
            description = "Permission: CUSTOMER_STATUS_CHANGE. A reason is mandatory and recorded in the status history. "
                    + "Allowed: ACTIVE<->BLOCKED, ACTIVE/BLOCKED->CLOSED. CLOSED is final.")
    @PreAuthorize("hasAuthority('CUSTOMER_STATUS_CHANGE')")
    public CustomerResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeCustomerStatusRequest request) {
        return customerService.changeStatus(id, request);
    }
}
