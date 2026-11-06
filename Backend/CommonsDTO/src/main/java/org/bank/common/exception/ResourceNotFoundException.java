package org.bank.common.exception;

import org.bank.common.error.ErrorCode;

public class ResourceNotFoundException extends BankException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found: " + identifier);
    }
}
