package org.bank.common.security;

import org.bank.common.security.testsupport.ProbeApplication;
import org.bank.common.security.testsupport.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ProbeApplication.class)
@AutoConfigureMockMvc
class BankSecurityIntegrationTest {

    private static final TestTokens TOKENS = new TestTokens();
    private static final TestTokens OTHER_ISSUER_KEY = new TestTokens();

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) throws IOException {
        Path publicKey = Files.createTempFile("bank-security-test", ".pem");
        publicKey.toFile().deleteOnExit();
        Files.writeString(publicKey, TOKENS.publicKeyPem());
        registry.add("bank.security.jwt.issuer", () -> TestTokens.ISSUER);
        registry.add("bank.security.jwt.public-key-location", () -> "file:" + publicKey.toAbsolutePath());
        registry.add("bank.security.public-paths", () -> "/probe/open");
    }

    @Autowired
    private MockMvc mockMvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void publicPathNeedsNoToken() throws Exception {
        mockMvc.perform(get("/probe/open")).andExpect(status().isOk());
    }

    @Test
    void protectedPathWithoutTokenReturnsStructured401() throws Exception {
        mockMvc.perform(get("/probe/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void malformedTokenReturns401InvalidToken() throws Exception {
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = TOKENS.token(TestTokens.ISSUER, Duration.ofMinutes(-5), "BR001", List.of("TELLER"), List.of());
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromWrongIssuerIsRejected() throws Exception {
        String token = TOKENS.token("https://evil.example", Duration.ofMinutes(5), "BR001", List.of("TELLER"), List.of());
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithUnknownKeyIsRejected() throws Exception {
        String forged = OTHER_ISSUER_KEY.valid("BR001", List.of("ADMIN"), List.of("USER_VIEW"));
        mockMvc.perform(get("/probe/admin").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenPopulatesAuthenticatedUser() throws Exception {
        String token = TOKENS.valid("BR001", List.of("TELLER"), List.of("USER_VIEW"));
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.branch").value("BR001"))
                .andExpect(jsonPath("$.mfa").value(true));
    }

    @Test
    void permissionAuthorityGrantsAndDenies() throws Exception {
        String allowed = TOKENS.valid("BR001", List.of("TELLER"), List.of("USER_VIEW"));
        String denied = TOKENS.valid("BR001", List.of("TELLER"), List.of("AUTH_LOGIN"));

        mockMvc.perform(get("/probe/permission").header(HttpHeaders.AUTHORIZATION, bearer(allowed)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/probe/permission").header(HttpHeaders.AUTHORIZATION, bearer(denied)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void roleAuthorityGrantsAndDenies() throws Exception {
        String admin = TOKENS.valid("HQ", List.of("ADMIN"), List.of());
        String teller = TOKENS.valid("BR001", List.of("TELLER"), List.of());

        mockMvc.perform(get("/probe/admin").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/probe/admin").header(HttpHeaders.AUTHORIZATION, bearer(teller)))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchScopingRestrictsNonBypassRoles() throws Exception {
        String teller = TOKENS.valid("BR001", List.of("TELLER"), List.of());
        String admin = TOKENS.valid("HQ", List.of("ADMIN"), List.of());

        mockMvc.perform(get("/probe/branch/BR001").header(HttpHeaders.AUTHORIZATION, bearer(teller)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/probe/branch/BR002").header(HttpHeaders.AUTHORIZATION, bearer(teller)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/probe/branch/BR002").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void wellFormedCorrelationIdIsEchoedAndMalformedIsReplaced() throws Exception {
        mockMvc.perform(get("/probe/open").header("X-Correlation-Id", "corr-123"))
                .andExpect(header().string("X-Correlation-Id", "corr-123"));

        String replaced = mockMvc.perform(get("/probe/open").header("X-Correlation-Id", "bad value\r\nInjected: x"))
                .andReturn().getResponse().getHeader("X-Correlation-Id");
        org.junit.jupiter.api.Assertions.assertNotEquals("bad value\r\nInjected: x", replaced);
        org.junit.jupiter.api.Assertions.assertTrue(replaced != null && replaced.matches("[A-Za-z0-9._-]{1,64}"));
    }
}
