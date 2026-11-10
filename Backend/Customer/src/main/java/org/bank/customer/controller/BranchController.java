package org.bank.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bank.customer.config.OpenApiConfig;
import org.bank.common.dto.response.BranchResponse;
import org.bank.common.dto.request.CreateBranchRequest;
import org.bank.common.dto.request.UpdateBranchRequest;
import org.bank.customer.service.BranchService;
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
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/branches")
@Tag(name = "Branches", description = "Bank branches. Requires a bearer token; the permission needed is listed per operation.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    @Operation(summary = "Create a branch",
            description = "Permission: BRANCH_MANAGE, and the branch code must be one the caller may access. Returns 201 with a Location header.")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE') and @branchAccess.canAccess(#request.code())")
    public ResponseEntity<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
        BranchResponse created = branchService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{code}").buildAndExpand(created.code()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List branches",
            description = "Permission: BRANCH_VIEW. Branch-restricted callers only see their own branch.")
    @PreAuthorize("hasAuthority('BRANCH_VIEW')")
    public List<BranchResponse> list() {
        return branchService.list();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a branch by code", description = "Permission: BRANCH_VIEW.")
    @PreAuthorize("hasAuthority('BRANCH_VIEW')")
    public BranchResponse get(@PathVariable String code) {
        return branchService.get(code);
    }

    @PatchMapping("/{code}")
    @Operation(summary = "Update a branch",
            description = "Permission: BRANCH_MANAGE. Partial update: fields left null are unchanged. "
                    + "Deactivating a branch stops new customers being registered there.")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE') and @branchAccess.canAccess(#code)")
    public BranchResponse update(@PathVariable String code, @Valid @RequestBody UpdateBranchRequest request) {
        return branchService.update(code, request);
    }
}
