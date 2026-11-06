package org.bank.identity.service;

import org.bank.common.dto.request.ChangePasswordRequest;
import org.bank.common.dto.request.CreateUserRequest;
import org.bank.common.dto.request.UpdateUserRequest;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.UserResponse;
import org.bank.identity.repository.UserSearchCriteria;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    UserResponse createUser(CreateUserRequest createUserRequest);

    /** Visible to the user themself and to callers whose branch scope covers the user. */
    UserResponse getUser(UUID userId);

    PageResponse<UserResponse> searchUsers(UserSearchCriteria criteria, Pageable pageable);

    UserResponse updateUser(UUID userId, UpdateUserRequest request);

    /** Self-service. Revokes every session so all devices must log in again. */
    void changePassword(UUID userId, ChangePasswordRequest request);

    void assignRoleToUser(UUID userId, Long roleId);

    void removeRoleFromUser(UUID userId, Long roleId);
}
