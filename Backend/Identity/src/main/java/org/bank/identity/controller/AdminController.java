package org.bank.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.RoleResponse;
import org.bank.common.dto.response.UserResponse;
import org.bank.identity.repository.LoginAttemptSearchCriteria;
import org.bank.common.dto.request.UpdateUserStatusRequest;
import org.bank.common.dto.response.LoginAttemptResponse;
import org.bank.identity.config.OpenApiConfig;
import org.bank.identity.service.AdminService;
import org.bank.identity.service.UserService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration", description = "Account status, role assignment and the login audit trail. Requires a bearer token; the permission needed is listed per operation.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminController {

    private static final Set<String> ATTEMPT_SORTABLE = Set.of("createdAt", "username", "result");
    private static final Sort ATTEMPT_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AdminService adminService;
    private final UserService userService;

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Change a user's status",
            description = "Permission: USER_LOCK. Status is ACTIVE, LOCKED or DISABLED; a reason is mandatory for the audit trail.")
    @PreAuthorize("hasAuthority('USER_LOCK')")
    public UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return adminService.changeUserStatus(id, request);
    }

    @PutMapping("/users/{id}/roles/{roleId}")
    @Operation(summary = "Assign a role to a user", description = "Permission: ROLE_ASSIGN.")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public ResponseEntity<Void> assignRole(@PathVariable UUID id, @PathVariable Long roleId) {
        userService.assignRoleToUser(id, roleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/roles/{roleId}")
    @Operation(summary = "Remove a role from a user", description = "Permission: ROLE_ASSIGN.")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public ResponseEntity<Void> removeRole(@PathVariable UUID id, @PathVariable Long roleId) {
        userService.removeRoleFromUser(id, roleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    @Operation(summary = "List roles", description = "Permission: USER_VIEW. The role catalogue.")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public List<RoleResponse> roles() {
        return adminService.listRoles();
    }

    @GetMapping("/login-attempts")
    @Operation(summary = "Search login attempts",
            description = "Permission: AUDIT_VIEW. Sortable fields: createdAt (default, newest first), username, result.")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<LoginAttemptResponse> loginAttempts(@ParameterObject @Valid LoginAttemptSearchCriteria criteria,
                                                            @ParameterObject Pageable pageable) {
        return adminService.searchLoginAttempts(criteria,
                SafePageable.restrict(pageable, ATTEMPT_SORTABLE, ATTEMPT_DEFAULT_SORT));
    }
}
