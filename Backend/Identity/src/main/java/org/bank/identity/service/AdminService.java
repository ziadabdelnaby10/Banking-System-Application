package org.bank.identity.service;

import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.RoleResponse;
import org.bank.common.dto.response.UserResponse;
import org.bank.identity.repository.LoginAttemptSearchCriteria;
import org.bank.common.dto.request.UpdateUserStatusRequest;
import org.bank.common.dto.response.LoginAttemptResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Administrative operations: user lifecycle status, role catalogue and the login audit trail. */
public interface AdminService {

    /** Activates, locks or disables a user. Anything but ACTIVE also revokes every session of the user. */
    UserResponse changeUserStatus(UUID userId, UpdateUserStatusRequest request);

    List<RoleResponse> listRoles();

    PageResponse<LoginAttemptResponse> searchLoginAttempts(LoginAttemptSearchCriteria criteria, Pageable pageable);
}
