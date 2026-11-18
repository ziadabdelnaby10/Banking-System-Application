package org.bank.account.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.bank.common.security.token.TokenClaims;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stand-in for the Identity service in tests. It signs RS256 access tokens with the claim contract from
 * {@link TokenClaims} (the same claims, in the same shape, as Identity issues) and publishes its public key as a
 * JWK set over HTTP. The Account service under test therefore validates tokens through its real production path
 * (JWKS download, signature, issuer, expiry), not through a test shortcut.
 */
public final class TestIdentity {

    /** Same value as the dev issuer: the Gateway URL that Identity writes into {@code iss}. */
    public static final String ISSUER = "http://localhost:9000";
    private static final String KEY_ID = "identity-test-key";

    private static final TestIdentity INSTANCE = new TestIdentity();

    private final HttpServer jwksServer;
    private final NimbusJwtEncoder signer;
    private final NimbusJwtEncoder strangerSigner;

    private TestIdentity() {
        try {
            RSAKey identityKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
            // Same key id, different key material: a forger copying Identity's kid must still be rejected.
            RSAKey strangerKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
            this.signer = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(identityKey)));
            this.strangerSigner = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(strangerKey)));

            byte[] jwks = new JWKSet(identityKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            this.jwksServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.jwksServer.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            this.jwksServer.setExecutor(null);
            this.jwksServer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> jwksServer.stop(0)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not start the test identity provider", ex);
        }
    }

    public static TestIdentity get() {
        return INSTANCE;
    }

    public String jwkSetUri() {
        return "http://localhost:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
    }

    /** A valid token, as Identity would issue it for {@code user}. */
    public String tokenFor(TestUser user) {
        return mint(signer, ISSUER, Duration.ofMinutes(15), user);
    }

    public String expiredTokenFor(TestUser user) {
        return mint(signer, ISSUER, Duration.ofMinutes(-10), user);
    }

    public String tokenFromOtherIssuer(TestUser user) {
        return mint(signer, "https://evil.example", Duration.ofMinutes(15), user);
    }

    /** Signed by a key Identity never published (but carrying Identity's key id). */
    public String forgedTokenFor(TestUser user) {
        return mint(strangerSigner, ISSUER, Duration.ofMinutes(15), user);
    }

    private static String mint(NimbusJwtEncoder encoder, String issuer, Duration validFor, TestUser user) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(validFor.isNegative() ? now.plus(validFor).minus(Duration.ofMinutes(5)) : now.minus(Duration.ofMinutes(1)))
                .expiresAt(now.plus(validFor))
                .claim(TokenClaims.USERNAME, user.username())
                .claim(TokenClaims.ROLES, List.copyOf(user.roles()))
                .claim(TokenClaims.PERMISSIONS, List.copyOf(user.permissions()))
                .claim(TokenClaims.MFA, true)
                .claim(TokenClaims.SESSION_ID, "1");
        if (user.branchCode() != null) {
            claims.claim(TokenClaims.BRANCH, user.branchCode());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
        try {
            return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Could not sign test token", ex);
        }
    }
}
