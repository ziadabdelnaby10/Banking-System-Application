package org.bank.common.security;

import org.bank.common.security.reactivetestsupport.ReactiveProbeApplication;
import org.bank.common.security.testsupport.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * WHAT THIS TEST PROVES: the new WebFlux (reactive) security behaves exactly like the servlet one:
 * public paths open, 401 with JSON for missing/bad tokens, the logged-in user is readable by handlers,
 * and the correlation id is generated, echoed and sanitized.
 * Same contract as {@link BankSecurityIntegrationTest}, proven on the WebFlux stack used by the Gateway.
 */
@SpringBootTest(classes = ReactiveProbeApplication.class, properties = "spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
class BankReactiveSecurityIntegrationTest {

    private static final TestTokens TOKENS = new TestTokens();

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) throws IOException {
        Path publicKey = Files.createTempFile("bank-reactive-security-test", ".pem");
        publicKey.toFile().deleteOnExit();
        Files.writeString(publicKey, TOKENS.publicKeyPem());
        registry.add("bank.security.jwt.issuer", () -> TestTokens.ISSUER);
        registry.add("bank.security.jwt.public-key-location", () -> "file:" + publicKey.toAbsolutePath());
        registry.add("bank.security.public-paths", () -> "/probe/open");
    }

    @Autowired
    private WebTestClient client;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void publicPathNeedsNoToken() {
        client.get().uri("/probe/open").exchange().expectStatus().isOk();
    }

    @Test
    void protectedPathWithoutTokenReturnsStructured401() {
        client.get().uri("/probe/me").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id")
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.path").isEqualTo("/probe/me");
    }

    @Test
    void errorBodyCarriesTheSameCorrelationIdAsTheHeader() {
        client.get().uri("/probe/me").header("X-Correlation-Id", "req-42.a").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-Id", "req-42.a")
                .expectBody().jsonPath("$.correlationId").isEqualTo("req-42.a");
    }

    @Test
    void malformedTokenReturns401InvalidToken() {
        client.get().uri("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_TOKEN");
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = TOKENS.token(TestTokens.ISSUER, Duration.ofMinutes(-5), "BR001", List.of("TELLER"), List.of());
        client.get().uri("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_TOKEN");
    }

    @Test
    void tokenFromWrongIssuerIsRejected() {
        String token = TOKENS.token("https://evil.example", Duration.ofMinutes(5), "BR001", List.of("TELLER"), List.of());
        client.get().uri("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = new TestTokens().valid("BR001", List.of("ADMIN"), List.of());
        client.get().uri("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(forged)).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validTokenReachesTheHandlerWithTheBankPrincipal() {
        String token = TOKENS.valid("BR001", List.of("TELLER"), List.of("USER_VIEW"));
        client.get().uri("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("tester:BR001");
    }

    @Test
    void correlationIdIsGeneratedAndForwardedToTheHandler() {
        String token = TOKENS.valid("BR001", List.of("TELLER"), List.of());
        String echoed = client.get().uri("/probe/correlation").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange().expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id")
                .returnResult(String.class).getResponseBody().blockFirst();
        org.assertj.core.api.Assertions.assertThat(echoed).isNotBlank();
    }

    @Test
    void hostileCorrelationIdIsReplacedNotReflected() {
        String token = TOKENS.valid("BR001", List.of("TELLER"), List.of());
        client.get().uri("/probe/correlation").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Correlation-Id", "bad value\twith spaces")
                .exchange().expectStatus().isOk()
                .expectHeader().value("X-Correlation-Id",
                        id -> org.assertj.core.api.Assertions.assertThat(id).matches("[A-Za-z0-9._-]{1,64}|[0-9a-f-]{36}"));
    }
}
