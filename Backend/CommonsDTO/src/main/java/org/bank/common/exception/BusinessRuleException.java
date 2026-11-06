package org.bank.common.exception;

import org.bank.common.error.ErrorCode;

public class BusinessRuleException extends BankException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
