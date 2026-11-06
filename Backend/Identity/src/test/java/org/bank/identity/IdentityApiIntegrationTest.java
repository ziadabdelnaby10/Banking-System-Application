package org.bank.identity;

import com.jayway.jsonpath.JsonPath;
import org.bank.identity.model.User;
import org.bank.identity.repository.UserRepository;
import org.bank.identity.security.AccessTokenIssuer;
import org.bank.identity.security.UserAuthorities;
import org.bank.identity.security.UserAuthorityResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of authentication, RBAC, lockout and user management against a real PostgreSQL
 * (citext/pgcrypto/inet are used by the schema, so H2 is not an option). Skipped when Docker is unavailable.
 * Seed data: V2 migration + dev-only password migration (all seeded users share {@link #DEV_PASSWORD}).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@ExtendWith(OutputCaptureExtension.class)
class IdentityApiIntegrationTest {

    private static final String DEV_PASSWORD = "Dev@Passw0rd!2026";
    private static final String NEW_USER_PASSWORD = "Str0ng!Passw0rd#1";
    private static final Pattern MFA_CODE = Pattern.compile("MFA code for \\S+ <[^>]+>: (\\d+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired UserAuthorityResolver authorityResolver;
    @Autowired AccessTokenIssuer accessTokenIssuer;
    @Autowired TransactionTemplate transactionTemplate;

    // ---------- authentication ----------

    @Test
    void loginIssuesTokensThatAuthenticateSubsequentCalls() throws Exception {
        String accessToken = loginWithoutMfa("teller1").accessToken();

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("teller1"))
                .andExpect(jsonPath("$.roles").value(hasItem("TELLER")));
    }

    @Test
    void loginResponsesAreNeverCached() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("teller1", DEV_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void anonymousCallersAreRejectedOnProtectedEndpoints() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameAnswer() throws Exception {
        String wrongPassword = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("teller1", "Wrong@Passw0rd!99")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nobody", DEV_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(unknownUser, "$.code")).isEqualTo("INVALID_CREDENTIALS");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(wrongPassword, "$.message"))
                .isEqualTo(JsonPath.read(unknownUser, "$.message"));
    }

    @Test
    void invalidRequestBodyYieldsFieldViolations() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isNotEmpty());
    }

    @Test
    void refreshRotatesTokenAndReuseOfTheOldOneKillsTheSession() throws Exception {
        Tokens first = loginWithoutMfa("teller1");

        Tokens second = refresh(first.refreshToken());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        // Reuse is treated as theft: the rotated token must be dead too.
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + second.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Tokens tokens = loginWithoutMfa("teller1");

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mfaUsersMustCompleteTheSecondFactor(CapturedOutput output) throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("manager1", DEV_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.tokens").doesNotExist())
                .andReturn();
        String challenge = JsonPath.read(login.getResponse().getContentAsString(), "$.mfaChallenge.challengeToken");

        mvc.perform(post("/api/v1/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challenge + "\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_INVALID"));

        Matcher matcher = MFA_CODE.matcher(output.getAll());
        org.assertj.core.api.Assertions.assertThat(matcher.find()).as("dev MFA code was logged").isTrue();
        String code = matcher.group(1);

        MvcResult verified = mvc.perform(post("/api/v1/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challenge + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String accessToken = JsonPath.read(verified.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("manager1"));
    }

    @Test
    void repeatedFailuresLockTheAccountEvenAgainstTheCorrectPassword() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(credentials("auditor1", "Wrong@Passw0rd!99")))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("auditor1", DEV_PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void jwksPublishesOnlyPublicKeyMaterial() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    // ---------- authorisation and user management ----------

    @Test
    void tellerCannotCreateUsers() throws Exception {
        String teller = loginWithoutMfa("teller1").accessToken();

        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + teller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("sneaky", "sneaky@bank.test", "BR-01", "[]")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanManageTheUserLifecycle() throws Exception {
        String admin = tokenFor("admin");
        String bearer = "Bearer " + admin;

        MvcResult roles = mvc.perform(get("/api/v1/admin/roles").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn();
        List<Integer> tellerIds = JsonPath.read(roles.getResponse().getContentAsString(), "$[?(@.name=='TELLER')].id");
        int tellerRoleId = tellerIds.get(0);

        MvcResult created = mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("newteller", "NewTeller@Bank.Test", "BR-01", "[" + tellerRoleId + "]")))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.username").value("newteller"))
                .andExpect(jsonPath("$.email").value("newteller@bank.test"))
                .andExpect(jsonPath("$.roles").value(hasItem("TELLER")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        String userId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // Duplicate username (different case) must conflict.
        mvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("NEWTELLER", "other@bank.test", "BR-01", "[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        mvc.perform(get("/api/v1/users").param("q", "newteller").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/v1/users").param("sort", "passwordHash,asc").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());

        // The new user can log in, then an admin lock revokes access.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("newteller", NEW_USER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));

        mvc.perform(patch("/api/v1/admin/users/{id}/status", userId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOCKED\",\"reason\":\"investigation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("newteller", NEW_USER_PASSWORD)))
                .andExpect(status().isLocked());

        mvc.perform(put("/api/v1/admin/users/{id}/roles/{roleId}", userId, tellerRoleId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/users/{id}/roles/{roleId}", userId, tellerRoleId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());

        mvc.perform(get("/api/v1/admin/login-attempts").param("username", "newteller").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void adminCannotChangeOwnStatus() throws Exception {
        mvc.perform(patch("/api/v1/admin/users/{id}/status", "11111111-1111-7111-8111-111111111111")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"oops\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    // ---------- helpers ----------

    private record Tokens(String accessToken, String refreshToken) {
    }

    private Tokens loginWithoutMfa(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, DEV_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.tokens.accessToken"), JsonPath.read(body, "$.tokens.refreshToken"));
    }

    private Tokens refresh(String refreshToken) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    /** Mints an access token directly for MFA-protected seed users (the second factor is covered separately). */
    private String tokenFor(String username) {
        return transactionTemplate.execute(status -> {
            User user = userRepository.findAll().stream()
                    .filter(candidate -> candidate.getUsername().equals(username)).findFirst().orElseThrow();
            UserAuthorities authorities = authorityResolver.resolve(user);
            return accessTokenIssuer.issue(user, authorities, 1L, true).value();
        });
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String createUserJson(String username, String email, String branch, String roleIds) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + NEW_USER_PASSWORD
                + "\",\"branchCode\":\"" + branch + "\",\"roleIds\":" + roleIds + "}";
    }
}
