package org.bank.identity.repository;

import org.bank.identity.model.UserRole;
import org.bank.identity.model.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    boolean existsByIdUserIdAndIdRoleId(UUID userId, Long roleId);

    List<UserRole> findByIdUserId(UUID userId);
}

