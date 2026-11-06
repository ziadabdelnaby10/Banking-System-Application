package org.bank.gateway.testsupport;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
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

/**
 * TEST HELPER: creates fake login tokens (JWTs) for tests. In real life only Identity can sign tokens
 * (it owns the private key). Here the test generates its own key pair, signs tokens with the private
 * key, and gives the Gateway the matching public key, so the Gateway can verify them exactly as it would
 * in production. Creating a token with a different TestJwts instance gives a token signed with a
 * "foreign" key, which the Gateway must reject.
 * Mints RS256 tokens shaped like the ones Identity issues (claims per CommonSecurity's token contract).
 */
public final class TestJwts {

    public static final String ISSUER = "https://identity.test";

    private final RSAPublicKey publicKey;
    private final NimbusJwtEncoder encoder;

    public TestJwts() {
        KeyPair pair = generateKeyPair();
        this.publicKey = (RSAPublicKey) pair.getPublic();
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test").build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** The public key in .pem text form; written to a temp file for the Gateway to load. */
    public String publicKeyPem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    /** Creates a token for the given issuer that expires after "validFor" (negative = already expired). */
    public String token(String issuer, Duration validFor) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(now.minus(Duration.ofMinutes(10)))
                .expiresAt(now.plus(validFor))
                .claim("preferred_username", "tester")
                .claim("branch", "BR001")
                .claim("roles", List.of("TELLER"))
                .claim("permissions", List.of("USER_VIEW"))
                .claim("mfa", true)
                .claim("sid", "1")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** A normal token: right issuer, valid for 5 more minutes. */
    public String valid() {
        return token(ISSUER, Duration.ofMinutes(5));
    }
}
