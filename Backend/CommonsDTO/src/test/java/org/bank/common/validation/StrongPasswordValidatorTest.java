package org.bank.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void acceptsPasswordMeetingPolicy() {
        assertTrue(validator.isValid("Str0ng!Passw0rd", null));
    }

    @Test
    void treatsNullAsValidSoNotBlankOwnsPresenceCheck() {
        assertTrue(validator.isValid(null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Sh0rt!Pw",                // too short
            "alllowercase1!x",         // no upper
            "ALLUPPERCASE1!X",         // no lower
            "NoDigitsHere!!!x",        // no digit
            "NoSymbolsHere123x"        // no symbol
    })
    void rejectsPasswordsBreakingPolicy(String candidate) {
        assertFalse(validator.isValid(candidate, null));
    }

    @Test
    void rejectsPasswordsAboveBcryptByteLimit() {
        assertFalse(validator.isValid("Aa1!" + "x".repeat(70), null));
    }
}
