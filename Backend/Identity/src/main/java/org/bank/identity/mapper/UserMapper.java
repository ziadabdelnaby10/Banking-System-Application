package org.bank.identity.mapper;

import org.bank.common.dto.request.CreateUserRequest;
import org.bank.common.dto.request.UpdateUserRequest;
import org.bank.common.dto.response.UserResponse;
import org.bank.identity.model.User;
import org.bank.identity.model.UserRole;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    @Mapping(target = "roles", source = "userRoles", qualifiedByName = "roleNames")
    UserResponse toResponse(User user);

    /**
     * Builds a new user. Credentials, status and lockout counters are never taken from the request:
     * the password arrives already hashed and everything else keeps the entity defaults.
     */
    @Mapping(target = "username", source = "request.username", qualifiedByName = "normalize")
    @Mapping(target = "email", source = "request.email", qualifiedByName = "normalize")
    @Mapping(target = "branchCode", source = "request.branchCode")
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "mfaEnabled", ignore = true)
    @Mapping(target = "failedLoginCount", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "lastFailedLoginAt", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    User toNewUser(CreateUserRequest request, String passwordHash);

    /** PATCH semantics: only fields present in the request are applied. */
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "email", source = "email", qualifiedByName = "normalize")
    @Mapping(target = "branchCode", source = "branchCode")
    @Mapping(target = "mfaEnabled", source = "mfaEnabled")
    void applyUpdate(UpdateUserRequest request, @MappingTarget User user);

    @Named("roleNames")
    default Set<String> roleNames(Set<UserRole> userRoles) {
        return userRoles.stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    /** Usernames/e-mails are stored lower-case: see UserRepository for why. */
    @Named("normalize")
    default String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
