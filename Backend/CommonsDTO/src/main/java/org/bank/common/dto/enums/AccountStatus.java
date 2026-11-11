package org.bank.common.dto.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle state of a bank account. CLOSED is terminal.
 *
 * <pre>
 * PENDING -> ACTIVE | CLOSED
 * ACTIVE  -> FROZEN | CLOSED
 * FROZEN  -> ACTIVE | CLOSED
 * </pre>
 */
public enum AccountStatus {
    PENDING,
    ACTIVE,
    FROZEN,
    CLOSED;

    private static final Set<AccountStatus> FROM_PENDING = EnumSet.of(ACTIVE, CLOSED);
    private static final Set<AccountStatus> FROM_ACTIVE = EnumSet.of(FROZEN, CLOSED);
    private static final Set<AccountStatus> FROM_FROZEN = EnumSet.of(ACTIVE, CLOSED);

    public boolean canTransitionTo(AccountStatus target) {
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(target);
            case ACTIVE -> FROM_ACTIVE.contains(target);
            case FROZEN -> FROM_FROZEN.contains(target);
            case CLOSED -> false;
        };
    }
}
