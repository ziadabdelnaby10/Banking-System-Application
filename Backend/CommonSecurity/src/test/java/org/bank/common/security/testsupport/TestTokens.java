package org.bank.common.security.testsupport;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.bank.common.security.token.TokenClaims;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Test helper that mints RS256 tokens shaped like the ones Identity issues. */
public final class TestTokens {

    public static final String ISSUER = "https://identity.test";

    private final RSAPublicKey publicKey;
    private final NimbusJwtEncoder encoder;

    public TestTokens() {
        KeyPair pair = generateKeyPair();
        this.publicKey = (RSAPublicKey) pair.getPublic();
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test").build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String publicKeyPem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    public String token(String issuer, Duration validFor, String branch, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(now.minus(Duration.ofMinutes(10)))
                .expiresAt(now.plus(validFor))
                .claim(TokenClaims.USERNAME, "tester")
                .claim(TokenClaims.BRANCH, branch)
                .claim(TokenClaims.ROLES, roles)
                .claim(TokenClaims.PERMISSIONS, permissions)
                .claim(TokenClaims.MFA, true)
                .claim(TokenClaims.SESSION_ID, "1")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String valid(String branch, List<String> roles, List<String> permissions) {
        return token(ISSUER, Duration.ofMinutes(5), branch, roles, permissions);
    }
}
