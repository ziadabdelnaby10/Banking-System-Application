package org.bank.gateway;

import org.bank.gateway.testsupport.DownstreamController;
import org.bank.gateway.testsupport.TestJwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WHAT THIS TEST PROVES: it starts the REAL Gateway and sends real HTTP requests to it, checking that
 *  1. no token -> 401, and the service is never called;
 *  2. bad tokens (garbage / expired / wrong issuer / signed with another key) -> 401;
 *  3. a good token passes and the token + correlation id reach the service unchanged;
 *  4. fake X-User-* headers are removed;
 *  5. public paths work without a token;
 *  6. browser CORS preflight requests (which carry no token) work for the allowed frontend origin and are
 *     refused for unknown websites.
 * Runs the real gateway (security chain, filters, routing) on a fixed free port and routes /api/** to a
 * stand-in downstream service inside the same JVM. /downstream/** is "internal": it is public here so the
 * only thing that can stop a request is the gateway itself.
 */
// Config Server / Eureka are switched off here (not in @DynamicPropertySource, which is applied too late): a running
// Config Server would otherwise replace the test routes and public paths with the real ones.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
@AutoConfigureWebTestClient
class GatewaySecurityIntegrationTest {

    private static final TestJwts JWTS = new TestJwts();
    private static final String ORIGIN = "http://localhost:5173";

    // Test-only settings injected before the app starts (they override application.yml).
    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) throws IOException {
        // Pick a free port; the Gateway listens on it and the fake service is reached on the same port.
        int port = TestSocketUtils.findAvailableTcpPort();
        // Give the Gateway the test key's public half, as a .pem file, so it can verify our test tokens.
        Path publicKey = Files.createTempFile("gateway-test-public", ".pem");
        publicKey.toFile().deleteOnExit();
        Files.writeString(publicKey, JWTS.publicKeyPem());

        registry.add("server.port", () -> port);
        // No Config Server / Eureka in tests: switch them off so the test is self-contained.
        registry.add("spring.cloud.config.enabled", () -> false);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("bank.security.jwt.issuer", () -> TestJwts.ISSUER);
        registry.add("bank.security.jwt.public-key-location", () -> "file:" + publicKey.toAbsolutePath());
        // Paths reachable without a token in this test: the fake service and one open API path.
        registry.add("bank.security.public-paths", () -> "/downstream/**,/api/open");

        // Index 0: the test source replaces the whole route list from application.yml.
        // Route: /api/xyz (what the client calls) is rewritten to /downstream/xyz on the fake service.
        String routes = "spring.cloud.gateway.server.webflux.routes[0].";
        registry.add(routes + "id", () -> "test-downstream");
        registry.add(routes + "uri", () -> "http://localhost:" + port);
        registry.add(routes + "predicates[0]", () -> "Path=/api/**");
        registry.add(routes + "filters[0]", () -> "RewritePath=/api/(?<segment>.*), /downstream/$\\{segment}");
    }

    // WebTestClient = a built-in HTTP client for tests; we use it to call the running Gateway.
    @Autowired
    private WebTestClient client;

    // Reset the "service was called" counter before every test.
    @BeforeEach
    void resetHits() {
        DownstreamController.HITS.set(0);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void requestWithoutTokenIsRejectedAndNeverReachesTheService() {
        client.get().uri("/api/echo").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.path").isEqualTo("/api/echo");

        assertThat(DownstreamController.HITS.get()).isZero();
    }

    @Test
    void invalidTokensAreRejectedAndNeverReachTheService() {
        String expired = JWTS.token(TestJwts.ISSUER, Duration.ofMinutes(-5));
        String wrongIssuer = JWTS.token("https://evil.example", Duration.ofMinutes(5));
        String foreignKey = new TestJwts().valid();

        for (String token : new String[]{"not-a-jwt", expired, wrongIssuer, foreignKey}) {
            client.get().uri("/api/echo").header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_TOKEN");
        }

        assertThat(DownstreamController.HITS.get()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void validTokenIsForwardedUnchangedTogetherWithTheCorrelationId() {
        String token = JWTS.valid();

        var result = client.get().uri("/api/echo").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Correlation-Id", "trace-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "trace-123")
                .expectBody(Map.class).returnResult();

        Map<String, String> received = (Map<String, String>) result.getResponseBody();
        assertThat(received).containsEntry("authorization", bearer(token));
        assertThat(received).containsEntry("x-correlation-id", "trace-123");
        assertThat(DownstreamController.HITS.get()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void spoofedIdentityHeadersNeverReachTheService() {
        var result = client.get().uri("/api/echo").header(HttpHeaders.AUTHORIZATION, bearer(JWTS.valid()))
                .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                .header("x-user-roles", "ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult();

        Map<String, String> received = (Map<String, String>) result.getResponseBody();
        assertThat(received.keySet()).noneMatch(name -> name.startsWith("x-user-"));
        assertThat(received).containsKey("authorization");
    }

    @Test
    void publicPathsNeedNoTokenAndStillGetACorrelationId() {
        client.get().uri("/api/open").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id");

        assertThat(DownstreamController.HITS.get()).isEqualTo(1);
    }

    @Test
    void corsPreflightFromTheFrontendIsAnsweredWithoutAToken() {
        client.options().uri("/api/echo")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN);

        assertThat(DownstreamController.HITS.get()).isZero();
    }

    @Test
    void corsPreflightFromAnUnknownOriginIsRefused() {
        client.options().uri("/api/echo")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }
}
