package org.bank.account.support;

import java.util.Set;
import java.util.UUID;

/**
 * A caller as Identity would describe them in a token. The factory methods use the same role-to-permission
 * mapping as Identity's V5 migration (V5__account_permissions.sql); if one changes, change the other.
 */
public record TestUser(UUID id, String username, String branchCode, Set<String> roles, Set<String> permissions) {

    private static final Set<String> TELLER_PERMISSIONS = Set.of("ACCOUNT_VIEW", "ACCOUNT_CREATE");
    private static final Set<String> MANAGER_PERMISSIONS = Set.of("ACCOUNT_VIEW", "ACCOUNT_CREATE", "ACCOUNT_STATUS_CHANGE");
    private static final Set<String> AUDITOR_PERMISSIONS = Set.of("ACCOUNT_VIEW");
    private static final Set<String> ADMIN_PERMISSIONS = Set.of("ACCOUNT_VIEW", "ACCOUNT_CREATE", "ACCOUNT_STATUS_CHANGE");

    public static TestUser teller(String branch) {
        return new TestUser(UUID.randomUUID(), "teller-" + branch, branch, Set.of("TELLER"), TELLER_PERMISSIONS);
    }

    public static TestUser manager(String branch) {
        return new TestUser(UUID.randomUUID(), "manager-" + branch, branch, Set.of("MANAGER"), MANAGER_PERMISSIONS);
    }

    /** Cross-branch read-only role (configured in bank.security.branch-bypass-roles). */
    public static TestUser auditor() {
        return new TestUser(UUID.randomUUID(), "auditor", "HQ", Set.of("AUDITOR"), AUDITOR_PERMISSIONS);
    }

    public static TestUser admin() {
        return new TestUser(UUID.randomUUID(), "admin", "HQ", Set.of("ADMIN"), ADMIN_PERMISSIONS);
    }

    public TestUser withPermissions(Set<String> newPermissions) {
        return new TestUser(id, username, branchCode, roles, newPermissions);
    }
}
