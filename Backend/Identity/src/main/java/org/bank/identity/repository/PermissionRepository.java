package org.bank.identity.repository;

import org.bank.identity.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /** Distinct permission codes granted through any of the given roles (single query, no entity loading). */
    @Query("""
            select distinct p.code
            from RolePermission rp, Permission p
            where rp.id.permissionId = p.id and rp.id.roleId in :roleIds
            """)
    Set<String> findCodesByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
