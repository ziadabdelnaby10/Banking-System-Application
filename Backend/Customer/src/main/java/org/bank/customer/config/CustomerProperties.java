package org.bank.customer.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param nationalIdPepper secret key mixed into the national-id fingerprint (HMAC). Without it the hash of a
 *                         short numeric id could be reversed by brute force. Supply it from a secret store.
 */
@Validated
@ConfigurationProperties(prefix = "bank.customer")
public record CustomerProperties(@NotBlank @Size(min = 16) String nationalIdPepper) {
}
