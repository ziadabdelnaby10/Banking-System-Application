package org.bank.identity.security;

import com.nimbusds.jose.jwk.RSAKey;
import org.bank.common.security.config.BankSecurityProperties;
import org.bank.common.security.token.TokenClaims;
import org.bank.identity.config.IdentityProperties;
import org.bank.identity.model.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Signs RS256 access tokens carrying the claim contract defined in {@link TokenClaims}. */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final String keyId;
    private final String issuer;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtAccessTokenIssuer(JwtEncoder jwtEncoder, RSAKey signingKey, BankSecurityProperties securityProperties,
                                IdentityProperties identityProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.keyId = signingKey.getKeyID();
        this.issuer = securityProperties.jwt().issuer();
        this.accessTtl = identityProperties.token().accessTtl();
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(User user, UserAuthorities authorities, long sessionId, boolean mfaVerified) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim(TokenClaims.USERNAME, user.getUsername())
                .claim(TokenClaims.ROLES, List.copyOf(authorities.roles()))
                .claim(TokenClaims.PERMISSIONS, List.copyOf(authorities.permissions()))
                .claim(TokenClaims.MFA, mfaVerified)
                .claim(TokenClaims.SESSION_ID, Long.toString(sessionId));
        if (user.getBranchCode() != null) {
            claims.claim(TokenClaims.BRANCH, user.getBranchCode());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedAccessToken(token, accessTtl.toSeconds());
    }
}
