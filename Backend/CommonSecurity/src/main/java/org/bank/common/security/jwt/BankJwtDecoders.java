package org.bank.common.security.jwt;

import org.bank.common.security.config.BankSecurityProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/** Builds the RS256 {@link JwtDecoder} used by every resource server. */
public final class BankJwtDecoders {

    private BankJwtDecoders() {
    }

    public static JwtDecoder create(BankSecurityProperties.Jwt jwt) {
        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(jwt.jwkSetUri())) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwt.jwkSetUri()).build();
        } else if (jwt.publicKeyLocation() != null) {
            decoder = NimbusJwtDecoder.withPublicKey(PemKeys.readPublicKey(jwt.publicKeyLocation())).build();
        } else {
            throw new IllegalStateException(
                    "Configure bank.security.jwt.public-key-location or bank.security.jwt.jwk-set-uri");
        }
        decoder.setJwtValidator(validator(jwt));
        return decoder;
    }

    /** Timestamps (with skew), issuer and a non-blank subject are mandatory for every token. */
    public static OAuth2TokenValidator<Jwt> validator(BankSecurityProperties.Jwt jwt) {
        return validator(jwt.issuer(), jwt.clockSkew());
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, java.time.Duration clockSkew) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(clockSkew),
                new JwtIssuerValidator(issuer),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText));
    }
}
