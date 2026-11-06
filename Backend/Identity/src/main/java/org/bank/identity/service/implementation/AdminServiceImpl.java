package org.bank.identity.service.implementation;

import lombok.RequiredArgsConstructor;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.RoleResponse;
import org.bank.common.dto.response.UserResponse;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.principal.CurrentUser;
import org.bank.identity.repository.LoginAttemptSearchCriteria;
import org.bank.common.dto.request.UpdateUserStatusRequest;
import org.bank.common.dto.response.LoginAttemptResponse;
import org.bank.identity.mapper.LoginAttemptMapper;
import org.bank.identity.mapper.PageResponses;
import org.bank.identity.mapper.RoleMapper;
import org.bank.identity.mapper.UserMapper;
import org.bank.identity.model.User;
import org.bank.common.dto.enums.UserStatus;
import org.bank.identity.repository.LoginAttemptRepository;
import org.bank.identity.repository.LoginAttemptSpecifications;
import org.bank.identity.repository.RoleRepository;
import org.bank.identity.repository.UserRepository;
import org.bank.identity.security.RefreshTokenService;
import org.bank.identity.service.AdminService;
import org.bank.identity.service.UserAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final LoginAttemptMapper loginAttemptMapper;
    private final RefreshTokenService refreshTokenService;
    private final UserAccessPolicy accessPolicy;

    @Override
    @Transactional
    public UserResponse changeUserStatus(UUID userId, UpdateUserStatusRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        accessPolicy.requireVisible(user);
        accessPolicy.requireNotSelf(userId, "change your own status");

        UserStatus previous = user.getStatus();
        UserStatus target = request.status();
        if (previous != target) {
            switch (target) {
                case ACTIVE -> user.activate();
                case LOCKED -> user.lock();
                case DISABLED -> user.disable();
            }
            if (target != UserStatus.ACTIVE) {
                refreshTokenService.revokeAllForUser(userId);
            }
            log.info("AUDIT user-status-changed actor={} user={} from={} to={} reason={}",
                    CurrentUser.require().userId(), userId, previous, target, sanitize(request.reason()));
        }
        return userMapper.toResponse(user);
    }

    @Override
    public List<RoleResponse> listRoles() {
        return roleMapper.toResponses(roleRepository.findAllByOrderByNameAsc());
    }

    @Override
    public PageResponse<LoginAttemptResponse> searchLoginAttempts(LoginAttemptSearchCriteria criteria, Pageable pageable) {
        return PageResponses.from(
                loginAttemptRepository.findAll(LoginAttemptSpecifications.matching(criteria), pageable),
                loginAttemptMapper::toResponse);
    }

    /** Free text goes into the log: strip line breaks so it cannot forge additional log entries. */
    private static String sanitize(String text) {
        return text == null ? "" : text.replaceAll("[\\r\\n\\t]+", " ");
    }
}
