package org.bank.common.security.reactive;

import org.bank.common.security.config.BankSecurityProperties;
import org.bank.common.security.jwt.BankJwtDecoders;
import org.bank.common.security.jwt.PemKeys;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Factory that builds the object which VERIFIES a JWT (login token): checks the signature with Identity's
 * public key, plus expiry, issuer and subject. Only Identity holds the private key (it signs tokens);
 * everyone else holds just the public key, so they can verify tokens but can never forge them.
 * Reactive twin of {@link BankJwtDecoders}. Separate class on purpose: servlet services must never load
 * Reactor types just because they use the decoder factory.
 */
public final class ReactiveBankJwtDecoders {

    private ReactiveBankJwtDecoders() {
    }

    public static ReactiveJwtDecoder create(BankSecurityProperties.Jwt jwt) {
        NimbusReactiveJwtDecoder decoder;
        // Option A: download the public key from Identity's JWKS URL (handy in dev / supports key rotation).
        if (StringUtils.hasText(jwt.jwkSetUri())) {
            decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwt.jwkSetUri()).build();
        // Option B: read a fixed public key from a .pem file (recommended for production).
        } else if (jwt.publicKeyLocation() != null) {
            decoder = NimbusReactiveJwtDecoder.withPublicKey(PemKeys.readPublicKey(jwt.publicKeyLocation())).build();
        } else {
            // Fail at startup instead of running with no way to verify tokens.
            throw new IllegalStateException(
                    "Configure bank.security.jwt.public-key-location or bank.security.jwt.jwk-set-uri");
        }
        // Same rules as the servlet side: timestamps (+skew), issuer, non-blank subject.
        decoder.setJwtValidator(BankJwtDecoders.validator(jwt));
        return decoder;
    }
}
