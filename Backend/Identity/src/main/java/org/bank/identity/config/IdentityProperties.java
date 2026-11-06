package org.bank.identity.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Identity-specific policy. Everything is externalised so security parameters can be tuned per
 * environment from the Config Server without a rebuild.
 */
@Validated
@ConfigurationProperties(prefix = "bank.identity")
public record IdentityProperties(
        @Valid @DefaultValue Lockout lockout,
        @Valid @DefaultValue Token token,
        @Valid @DefaultValue Mfa mfa,
        @Valid @DefaultValue Password password
) {

    /**
     * @param maxFailedAttempts consecutive failures before an automatic lockout
     * @param lockDuration      how long the automatic lockout lasts
     */
    public record Lockout(@DefaultValue("5") @Min(1) int maxFailedAttempts,
                          @DefaultValue("15m") @NotNull Duration lockDuration) {
    }

    /**
     * @param accessTtl           access token lifetime (keep short; permissions are embedded)
     * @param refreshTtl          refresh token / session lifetime, renewed on every rotation
     * @param sessionRetention    how long expired/revoked sessions are kept before purge
     * @param privateKeyLocation  PKCS#8 PEM RSA private key used to sign tokens (mandatory outside dev)
     * @param allowEphemeralKey   dev only: generate a throw-away key when none is configured
     */
    public record Token(@DefaultValue("15m") @NotNull Duration accessTtl,
                        @DefaultValue("8h") @NotNull Duration refreshTtl,
                        @DefaultValue("7d") @NotNull Duration sessionRetention,
                        Resource privateKeyLocation,
                        @DefaultValue("false") boolean allowEphemeralKey) {
    }

    public record Mfa(@DefaultValue("5m") @NotNull Duration codeTtl,
                      @DefaultValue("5") @Min(1) int maxAttempts,
                      @DefaultValue("6") @Min(6) @Max(10) int codeLength) {
    }

    /** @param bcryptStrength bcrypt cost factor; 12 is the minimum recommended for new deployments */
    public record Password(@DefaultValue("12") @Min(10) @Max(16) int bcryptStrength) {
    }
}
