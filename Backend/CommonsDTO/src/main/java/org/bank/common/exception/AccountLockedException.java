package org.bank.common.exception;

import org.bank.common.error.ErrorCode;

import java.time.Instant;

public class AccountLockedException extends BankException {

    private final transient Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super(ErrorCode.ACCOUNT_LOCKED, "Account is temporarily locked");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
