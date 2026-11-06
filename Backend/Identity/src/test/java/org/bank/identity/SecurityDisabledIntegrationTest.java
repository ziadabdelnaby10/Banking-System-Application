package org.bank.identity;

import org.bank.identity.config.SecurityDisabledConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WHAT THIS PROVES: with {@code bank.security.enabled=false} (local testing switch) protected endpoints
 * answer WITHOUT a token, and the caller is the built-in local ADMIN, so audit/branch logic still works.
 * Needs Docker (real PostgreSQL), like the other integration test.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "bank.security.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class SecurityDisabledIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired(required = false) SecurityDisabledConfiguration disabledConfiguration;

    @Test
    void bypassConfigurationIsActive() {
        org.assertj.core.api.Assertions.assertThat(disabledConfiguration).isNotNull();
    }

    @Test
    void protectedEndpointsWorkWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/admin/roles")).andExpect(status().isOk());
    }

    @Test
    void branchScopedListingSeesEveryBranchBecauseCallerIsLocalAdmin() throws Exception {
        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty());
    }
}
