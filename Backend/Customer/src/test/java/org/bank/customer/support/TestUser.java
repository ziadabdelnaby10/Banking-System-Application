package org.bank.customer.support;

import java.util.Set;
import java.util.UUID;

/**
 * A caller as Identity would describe them in a token. The factory methods use the same role-to-permission
 * mapping as Identity's V4 migration (V4__customer_permissions.sql); if one changes, change the other.
 */
public record TestUser(UUID id, String username, String branchCode, Set<String> roles, Set<String> permissions) {

    private static final Set<String> TELLER_PERMISSIONS = Set.of(
            "BRANCH_VIEW", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "KYC_SUBMIT");
    private static final Set<String> MANAGER_PERMISSIONS = Set.of(
            "BRANCH_VIEW", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_STATUS_CHANGE", "KYC_REVIEW");
    private static final Set<String> AUDITOR_PERMISSIONS = Set.of("BRANCH_VIEW", "CUSTOMER_VIEW");
    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            "BRANCH_VIEW", "BRANCH_MANAGE", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_UPDATE",
            "CUSTOMER_STATUS_CHANGE", "KYC_SUBMIT", "KYC_REVIEW");

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
