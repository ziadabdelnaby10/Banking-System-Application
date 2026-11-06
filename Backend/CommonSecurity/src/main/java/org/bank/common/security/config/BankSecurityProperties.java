package org.bank.common.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Type-safe configuration for the shared security starter.
 *
 * <pre>
 * bank:
 *   security:
 *     jwt:
 *       issuer: https://identity.bank.local
 *       public-key-location: file:/run/secrets/jwt-public.pem   # or jwk-set-uri
 * </pre>
 *
 * @param enabled           set to false to opt out of the starter entirely
 * @param publicPaths       ant-style paths that skip authentication (keep this list minimal)
 * @param jwt               token validation settings
 * @param branchBypassRoles roles that may access every branch (branch scoping is skipped)
 */
@Validated
@ConfigurationProperties(prefix = "bank.security")
public record BankSecurityProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue({"/actuator/health/**", "/actuator/info"}) List<String> publicPaths,
        @Valid @DefaultValue Jwt jwt,
        @DefaultValue("ADMIN") Set<String> branchBypassRoles
) {

    /**
     * @param issuer           expected {@code iss} claim; mandatory so tokens from other issuers are rejected
     * @param publicKeyLocation PEM (X.509 "BEGIN PUBLIC KEY") RSA public key used to verify RS256 signatures
     * @param jwkSetUri        alternative to {@code publicKeyLocation}: JWKS endpoint (supports key rotation)
     * @param clockSkew        tolerated clock drift for exp/nbf checks
     */
    public record Jwt(
            @NotBlank String issuer,
            Resource publicKeyLocation,
            String jwkSetUri,
            @DefaultValue("30s") Duration clockSkew
    ) {
    }
}
