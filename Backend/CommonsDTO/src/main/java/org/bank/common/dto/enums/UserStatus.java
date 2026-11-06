package org.bank.common.dto.enums;

public enum UserStatus {
    ACTIVE,
    /** Administratively locked; stays locked until an administrator unlocks the user. */
    LOCKED,
    DISABLED
}
