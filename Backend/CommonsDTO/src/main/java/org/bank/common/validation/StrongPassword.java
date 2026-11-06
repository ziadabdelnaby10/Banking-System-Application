package org.bank.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bank password policy: 12+ characters, at most 72 UTF-8 bytes (bcrypt input limit),
 * with upper case, lower case, digit and symbol. Null is considered valid; combine with @NotBlank.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must be 12-72 characters and contain upper case, lower case, digit and symbol";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
