package org.bank.common.exception;

import org.bank.common.error.ErrorCode;

public class DuplicateResourceException extends BankException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
