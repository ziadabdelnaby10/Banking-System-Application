package org.bank.gateway;

import org.bank.gateway.testsupport.TestJwts;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WHAT THIS TEST PROVES: the Gateway is the front page for API documentation.
 *  - dev profile: Swagger UI, its config (the service dropdown) and the docs routes need no token, while the
 *    real APIs still do;
 *  - prod profile: the documentation is gone (nothing public, nothing served).
 */
class GatewaySwaggerIntegrationTest {

    private static final TestJwts JWTS = new TestJwts();

    // Same self-contained setup as GatewaySecurityIntegrationTest: no Eureka, test signing key.
    // (Config Server is switched off through @SpringBootTest(properties): a @DynamicPropertySource is applied
    // too late to stop the import, and a running Config Server would silently replace the local YAML.)
    static void commonProperties(DynamicPropertyRegistry registry) throws IOException {
        Path publicKey = Files.createTempFile("gateway-swagger-test-public", ".pem");
        publicKey.toFile().deleteOnExit();
        Files.writeString(publicKey, JWTS.publicKeyPem());

        registry.add("bank.security.jwt.issuer", () -> TestJwts.ISSUER);
        registry.add("bank.security.jwt.public-key-location", () -> "file:" + publicKey.toAbsolutePath());
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
    @AutoConfigureWebTestClient
    @ActiveProfiles("dev")
    class DevProfile {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) throws IOException {
            commonProperties(registry);
        }

        @Autowired
        WebTestClient client;

        @Test
        void rootRedirectsToSwaggerUiWithoutAToken() {
            client.get().uri("/").exchange()
                    .expectStatus().isFound()
                    .expectHeader().valueEquals("Location", "/swagger-ui.html");
        }

        @Test
        void swaggerUiIsPublicInDev() {
            var status = client.get().uri("/swagger-ui.html").exchange().returnResult(String.class).getStatus();
            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(status.is2xxSuccessful() || status.is3xxRedirection()).isTrue();
        }

        @Test
        void swaggerConfigListsEveryServiceInTheDropdown() {
            client.get().uri("/v3/api-docs/swagger-config").exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.urls[0].name").isEqualTo("identity-service")
                    .jsonPath("$.urls[0].url").isEqualTo("/docs/identity/v3/api-docs");
        }

        @Test
        void identityDocsRouteIsPublicButTheApiItDescribesIsNot() {
            // No Eureka here, so the forward itself fails (not 401 = the Gateway let it through).
            var docs = client.get().uri("/docs/identity/v3/api-docs").exchange().returnResult(String.class).getStatus();
            assertThat(docs).isNotEqualTo(HttpStatus.UNAUTHORIZED);

            client.get().uri("/api/v1/users/me").exchange().expectStatus().isUnauthorized();
            client.get().uri("/api/v1/admin/roles").exchange().expectStatus().isUnauthorized();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
    @AutoConfigureWebTestClient
    @ActiveProfiles("prod")
    class ProdProfile {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) throws IOException {
            commonProperties(registry);
        }

        @Autowired
        WebTestClient client;

        @Test
        void documentationIsNotPublicInProd() {
            for (String path : new String[]{"/", "/swagger-ui.html", "/v3/api-docs/swagger-config", "/docs/identity/v3/api-docs"}) {
                client.get().uri(path).exchange().expectStatus().isUnauthorized();
            }
        }

        @Test
        void loginStaysPublicInProd() {
            // Not 401: the Gateway lets it through (the forward fails later because no Eureka is running).
            var status = client.post().uri("/api/v1/auth/login").exchange().returnResult(String.class).getStatus();
            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
