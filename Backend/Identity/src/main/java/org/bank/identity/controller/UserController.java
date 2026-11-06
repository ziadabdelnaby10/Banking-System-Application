package org.bank.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bank.common.dto.request.ChangePasswordRequest;
import org.bank.common.dto.request.CreateUserRequest;
import org.bank.common.dto.request.UpdateUserRequest;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.UserResponse;
import org.bank.common.security.principal.AuthenticatedUser;
import org.bank.identity.config.OpenApiConfig;
import org.bank.identity.repository.UserSearchCriteria;
import org.bank.identity.service.UserService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management and self service. Requires a bearer token; the permission needed is listed per operation.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

    private static final Set<String> SORTABLE = Set.of("username", "email", "status", "createdAt", "lastLoginAt", "branchCode");
    private static final Sort DEFAULT_SORT = Sort.by("username");

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a user",
            description = "Permission: USER_CREATE, and the target branch must be one the caller may access. "
                    + "Returns 201 with a Location header.")
    @PreAuthorize("hasAuthority('USER_CREATE') and @branchAccess.canAccess(#request.branchCode())")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Search users",
            description = "Permission: USER_VIEW. Branch-restricted callers only see their own branch. "
                    + "Sortable fields: username, email, status, createdAt, lastLoginAt, branchCode.")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public PageResponse<UserResponse> search(@ParameterObject @Valid UserSearchCriteria criteria,
                                             @ParameterObject Pageable pageable) {
        return userService.searchUsers(criteria, SafePageable.restrict(pageable, SORTABLE, DEFAULT_SORT));
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", description = "Any authenticated user.")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return userService.getUser(caller.userId());
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change own password", description = "Any authenticated user; the current password is required.")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal AuthenticatedUser caller,
                                                  @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(caller.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id", description = "Permission: USER_VIEW.")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserResponse get(@PathVariable UUID id) {
        return userService.getUser(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a user",
            description = "Permission: USER_UPDATE. Partial update: fields left null are unchanged.")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request);
    }
}
