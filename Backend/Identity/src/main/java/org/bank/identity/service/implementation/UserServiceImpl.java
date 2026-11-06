package org.bank.identity.service.implementation;

import lombok.RequiredArgsConstructor;
import org.bank.common.dto.request.ChangePasswordRequest;
import org.bank.common.dto.request.CreateUserRequest;
import org.bank.common.dto.request.UpdateUserRequest;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.UserResponse;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.exception.DuplicateResourceException;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.principal.CurrentUser;
import org.bank.identity.repository.UserSearchCriteria;
import org.bank.identity.mapper.PageResponses;
import org.bank.identity.mapper.UserMapper;
import org.bank.identity.model.Role;
import org.bank.identity.model.User;
import org.bank.identity.repository.RoleRepository;
import org.bank.identity.repository.UserRepository;
import org.bank.identity.repository.UserSpecifications;
import org.bank.identity.security.RefreshTokenService;
import org.bank.identity.service.UserAccessPolicy;
import org.bank.identity.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserAccessPolicy accessPolicy;
    private final BranchAccessEvaluator branchAccess;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = userMapper.normalize(request.username());
        String email = userMapper.normalize(request.email());
        // Fast, friendly checks; the unique constraints remain the real guard against races (-> 409).
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username is already in use");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("E-mail is already in use");
        }

        List<Role> roles = loadRoles(request.roleIds());
        roles.forEach(accessPolicy::requireCanManageRole);

        User user = userRepository.save(userMapper.toNewUser(request, passwordEncoder.encode(request.password())));
        roles.forEach(user::addRole);

        log.info("AUDIT user-created actor={} user={} branch={} roles={}",
                CurrentUser.get().map(c -> c.userId().toString()).orElse("system"),
                user.getId(), user.getBranchCode(), roles.stream().map(Role::getName).toList());
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUser(UUID userId) {
        User user = findWithRoles(userId);
        accessPolicy.requireVisible(user);
        return userMapper.toResponse(user);
    }

    @Override
    public PageResponse<UserResponse> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        Page<User> page = userRepository.findAll(
                UserSpecifications.matching(criteria, branchAccess.restrictedBranch()), pageable);
        return PageResponses.from(page, userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = findWithRoles(userId);
        accessPolicy.requireVisible(user);

        if (request.email() != null) {
            String email = userMapper.normalize(request.email());
            if (!email.equals(user.getEmail()) && userRepository.existsByEmailAndIdNot(email, userId)) {
                throw new DuplicateResourceException("E-mail is already in use");
            }
        }
        if (request.branchCode() != null && !request.branchCode().equals(user.getBranchCode())
                && !branchAccess.canAccess(request.branchCode())) {
            throw new AccessDeniedException("Target branch is outside your scope");
        }
        boolean mfaChanged = request.mfaEnabled() != null && !request.mfaEnabled().equals(user.getMfaEnabled());
        if (mfaChanged && !request.mfaEnabled()) {
            accessPolicy.requireNotSelf(userId, "disable your own multi-factor authentication");
        }

        userMapper.applyUpdate(request, user);

        if (mfaChanged) {
            // Existing sessions were established under the old MFA setting.
            refreshTokenService.revokeAllForUser(userId);
        }
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!matches(request.currentPassword(), user.getPasswordHash())) {
            // 422 (not 401): a wrong current password must not make clients think the session expired.
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(userId);
        log.info("AUDIT password-changed user={}", userId);
    }

    @Override
    @Transactional
    public void assignRoleToUser(UUID userId, Long roleId) {
        User user = findWithRoles(userId);
        accessPolicy.requireVisible(user);
        accessPolicy.requireNotSelf(userId, "change your own roles");
        Role role = findRole(roleId);
        accessPolicy.requireCanManageRole(role);

        int before = user.getUserRoles().size();
        user.addRole(role);
        if (user.getUserRoles().size() != before) {
            onRolesChanged(user, role, "assigned");
        }
    }

    @Override
    @Transactional
    public void removeRoleFromUser(UUID userId, Long roleId) {
        User user = findWithRoles(userId);
        accessPolicy.requireVisible(user);
        accessPolicy.requireNotSelf(userId, "change your own roles");
        Role role = findRole(roleId);
        accessPolicy.requireCanManageRole(role);

        int before = user.getUserRoles().size();
        user.removeRole(roleId);
        if (user.getUserRoles().size() != before) {
            onRolesChanged(user, role, "removed");
        }
    }

    private void onRolesChanged(User user, Role role, String action) {
        // Access tokens embed roles for up to accessTtl; revoking sessions stops silent renewal of stale grants.
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("AUDIT role-{} actor={} user={} role={}", action, CurrentUser.require().userId(), user.getId(), role.getName());
    }

    private User findWithRoles(UUID userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Role findRole(Long roleId) {
        return roleRepository.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    }

    private List<Role> loadRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != new HashSet<>(roleIds).size()) {
            throw new ResourceNotFoundException("Role", roleIds);
        }
        return roles;
    }

    private boolean matches(String rawPassword, String hash) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                && passwordEncoder.matches(rawPassword, hash);
    }
}
