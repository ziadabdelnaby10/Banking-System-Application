package org.bank.identity.security;

import lombok.RequiredArgsConstructor;
import org.bank.identity.model.User;
import org.bank.identity.model.UserRole;
import org.bank.identity.repository.PermissionRepository;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

/** Resolves the effective RBAC grants of a user. Must be called inside a transaction (lazy roles). */
@Component
@RequiredArgsConstructor
public class UserAuthorityResolver {

    private final PermissionRepository permissionRepository;

    public UserAuthorities resolve(User user) {
        Set<String> roleNames = new TreeSet<>();
        Set<Long> roleIds = new java.util.HashSet<>();
        for (UserRole userRole : user.getUserRoles()) {
            roleIds.add(userRole.getId().getRoleId());
            roleNames.add(userRole.getRole().getName());
        }
        Set<String> permissions = roleIds.isEmpty()
                ? Set.of()
                : new TreeSet<>(permissionRepository.findCodesByRoleIds(roleIds));
        return new UserAuthorities(roleNames, permissions);
    }
}
