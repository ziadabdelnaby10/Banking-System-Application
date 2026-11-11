package org.bank.common.dto.enums;

import java.util.EnumSet;
import java.util.Set;

/** Operational state of a customer. CLOSED is terminal. */
public enum CustomerStatus {
    ACTIVE,
    BLOCKED,
    CLOSED;

    private static final Set<CustomerStatus> FROM_ACTIVE = EnumSet.of(BLOCKED, CLOSED);
    private static final Set<CustomerStatus> FROM_BLOCKED = EnumSet.of(ACTIVE, CLOSED);

    public boolean canTransitionTo(CustomerStatus target) {
        return switch (this) {
            case ACTIVE -> FROM_ACTIVE.contains(target);
            case BLOCKED -> FROM_BLOCKED.contains(target);
            case CLOSED -> false;
        };
    }
}
