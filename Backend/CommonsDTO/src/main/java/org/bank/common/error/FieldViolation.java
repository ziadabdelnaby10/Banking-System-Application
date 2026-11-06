package org.bank.common.error;

/**
 * A single field-level validation failure. The rejected value is intentionally not included
 * so passwords and other sensitive input can never leak through error payloads.
 */
public record FieldViolation(String field, String message) {
}
