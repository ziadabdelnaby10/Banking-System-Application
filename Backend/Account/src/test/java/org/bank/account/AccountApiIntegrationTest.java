package org.bank.account;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.bank.account.service.AccountService;
import org.bank.account.support.TestIdentity;
import org.bank.account.support.TestUser;
import org.bank.common.dto.request.ApplyBalanceSnapshotRequest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the Account service against a real PostgreSQL (Flyway schema, constraints, indexes),
 * with requests authenticated by tokens in the format Identity issues ({@link TestIdentity} plays Identity).
 * Skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void identityPublicKeys(DynamicPropertyRegistry registry) {
        registry.add("bank.security.jwt.jwk-set-uri", TestIdentity.get()::jwkSetUri);
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accountService;

    private final TestIdentity identity = TestIdentity.get();

    // ---------------------------------------------------------------- Authentication and authorisation

    @Nested
    class Security {

        @Test
        void callersWithoutAValidTokenAreRejected() throws Exception {
            mvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + identity.expiredTokenFor(TestUser.teller("BR001"))))
                    .andExpect(status().isUnauthorized());
            mvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + identity.tokenFromOtherIssuer(TestUser.admin())))
                    .andExpect(status().isUnauthorized());
            mvc.perform(MockMvcRequestBuilders.get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + identity.forgedTokenFor(TestUser.admin())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void aValidTokenWithoutThePermissionIsForbidden() throws Exception {
            send(get("/api/v1/accounts"), TestUser.teller("BR001").withPermissions(Set.of("ACCOUNT_CREATE")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        void tellersCannotChangeAccountStatus() throws Exception {
            String accountId = openAccount(TestUser.teller("BR001"), "BR001");

            send(post("/api/v1/accounts/" + accountId + "/freeze"), TestUser.teller("BR001"), "{\"reason\":\"test\"}")
                    .andExpect(status().isForbidden());
        }

        @Test
        void tellersCannotOpenAccountsInAnotherBranch() throws Exception {
            send(post("/api/v1/accounts"), TestUser.teller("BR001"), openBody(UUID.randomUUID(), "SAVINGS_STD", "EGP", "BR002"))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- Opening accounts

    @Nested
    class Opening {

        @Test
        void openCreatesAnActiveAccountWithHistoryAndOutboxEvent() throws Exception {
            UUID customerId = UUID.randomUUID();
            TestUser teller = TestUser.teller("BR001");

            ResultActions result = send(post("/api/v1/accounts"), teller, openBody(customerId, "savings_std", "EGP", "br001"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", startsWith("http://localhost/api/v1/accounts/")))
                    .andExpect(jsonPath("$.accountNo").value(startsWith("ACC")))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.branchCode").value("BR001"))
                    .andExpect(jsonPath("$.productCode").value("SAVINGS_STD"))
                    .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                    .andExpect(jsonPath("$.currency").value("EGP"))
                    .andExpect(jsonPath("$.ledgerBalance").value(0.0))
                    .andExpect(jsonPath("$.availableBalance").value(0.0))
                    .andExpect(jsonPath("$.createdBy").value(teller.id().toString()));
            String accountId = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");

            Integer historyRows = jdbc.queryForObject(
                    "select count(*) from account_status_history where account_id = ?::uuid and previous_status is null "
                            + "and new_status = 'ACTIVE' and changed_by = ?::uuid", Integer.class, accountId, teller.id().toString());
            List<String> events = jdbc.queryForList(
                    "select payload::text from outbox_events where aggregate_id = ?::uuid and event_type = 'account.opened'",
                    String.class, accountId);

            assertThat(historyRows).isEqualTo(1);
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).contains(customerId.toString()).doesNotContain("balance");
        }

        @Test
        void accountNumbersAreUnique() throws Exception {
            String first = JsonPath.read(open(TestUser.teller("BR001"), UUID.randomUUID(), "BR001"), "$.accountNo");
            String second = JsonPath.read(open(TestUser.teller("BR001"), UUID.randomUUID(), "BR001"), "$.accountNo");

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void unknownProductIsNotFound() throws Exception {
            send(post("/api/v1/accounts"), TestUser.teller("BR001"), openBody(UUID.randomUUID(), "NOPE", "EGP", "BR001"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        void invalidRequestsAreRejectedWithFieldViolations() throws Exception {
            send(post("/api/v1/accounts"), TestUser.teller("BR001"), "{\"productCode\":\"SAVINGS_STD\",\"currency\":\"egp\",\"branchCode\":\"BR001\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[?(@.field=='customerId')]").exists())
                    .andExpect(jsonPath("$.violations[?(@.field=='currency')]").exists());
        }

        @Test
        void productsAreListedFromTheCatalogue() throws Exception {
            send(get("/api/v1/account-products"), TestUser.teller("BR001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].code").value("CURRENT_STD"))
                    .andExpect(jsonPath("$[1].code").value("SAVINGS_STD"));
        }
    }

    // ---------------------------------------------------------------- Reading and branch scoping

    @Nested
    class Reading {

        @Test
        void callersOnlySeeAccountsOfTheirOwnBranch() throws Exception {
            UUID customerId = UUID.randomUUID();
            open(TestUser.admin(), customerId, "BR001");
            open(TestUser.admin(), customerId, "BR002");

            send(get("/api/v1/accounts?customerId=" + customerId), TestUser.teller("BR001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].branchCode").value("BR001"));
            // A client-supplied branch filter cannot widen the scope of a branch-restricted caller.
            send(get("/api/v1/accounts?customerId=" + customerId + "&branchCode=BR002"), TestUser.teller("BR001"))
                    .andExpect(jsonPath("$.content[0].branchCode").value("BR001"));
            send(get("/api/v1/accounts?customerId=" + customerId), TestUser.auditor())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void accountsOutsideTheCallersBranchAreNotFound() throws Exception {
            String accountId = openAccount(TestUser.admin(), "BR002");

            for (String path : List.of("", "/balance", "/status-history")) {
                send(get("/api/v1/accounts/" + accountId + path), TestUser.teller("BR001"))
                        .andExpect(status().isNotFound());
            }
            send(get("/api/v1/accounts/" + accountId), TestUser.teller("BR002")).andExpect(status().isOk());
        }

        @Test
        void searchSupportsFiltersPrefixAndSafeSorting() throws Exception {
            UUID customerId = UUID.randomUUID();
            String accountNo = JsonPath.read(open(TestUser.admin(), customerId, "BR001"), "$.accountNo");

            send(get("/api/v1/accounts?accountNo=" + accountNo.substring(0, 8) + "&customerId=" + customerId + "&status=ACTIVE"),
                    TestUser.teller("BR001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].accountNo").value(accountNo));
            send(get("/api/v1/accounts?customerId=" + customerId + "&status=CLOSED"), TestUser.teller("BR001"))
                    .andExpect(jsonPath("$.totalElements").value(0));
            send(get("/api/v1/accounts?accountNo=%25&customerId=" + customerId), TestUser.teller("BR001"))
                    .andExpect(jsonPath("$.totalElements").value(0));
            send(get("/api/v1/accounts?sort=customerId,asc"), TestUser.teller("BR001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
            send(get("/api/v1/accounts?sort=accountNo,asc"), TestUser.teller("BR001")).andExpect(status().isOk());
        }

        @Test
        void balanceStartsAtZero() throws Exception {
            String accountId = openAccount(TestUser.teller("BR001"), "BR001");

            send(get("/api/v1/accounts/" + accountId + "/balance"), TestUser.teller("BR001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(accountId))
                    .andExpect(jsonPath("$.ledgerBalance").value(0.0))
                    .andExpect(jsonPath("$.availableBalance").value(0.0))
                    .andExpect(jsonPath("$.asOf").exists());
        }
    }

    // ---------------------------------------------------------------- Lifecycle

    @Nested
    class Lifecycle {

        @Test
        void freezeUnfreezeAndCloseAreRecordedNewestFirst() throws Exception {
            TestUser manager = TestUser.manager("BR001");
            String accountId = openAccount(manager, "BR001");
            String path = "/api/v1/accounts/" + accountId;

            send(post(path + "/freeze"), manager, "{\"reason\":\"  Fraud review  \"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FROZEN"))
                    .andExpect(jsonPath("$.updatedBy").value(manager.id().toString()))
                    .andExpect(jsonPath("$.version").value(1));
            send(post(path + "/freeze"), manager, "{\"reason\":\"again\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
            send(post(path + "/unfreeze"), manager, "{\"reason\":\"Cleared\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
            send(post(path + "/close"), manager, "{\"reason\":\"Customer request\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.closedAt").exists());
            send(post(path + "/unfreeze"), manager, "{\"reason\":\"nope\"}")
                    .andExpect(status().isUnprocessableEntity());

            send(get(path + "/status-history"), manager)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4))
                    .andExpect(jsonPath("$.content[0].newStatus").value("CLOSED"))
                    .andExpect(jsonPath("$.content[1].newStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.content[2].newStatus").value("FROZEN"))
                    .andExpect(jsonPath("$.content[2].previousStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.content[2].reason").value("Fraud review"))
                    .andExpect(jsonPath("$.content[3].previousStatus").doesNotExist());
            Integer statusEvents = jdbc.queryForObject(
                    "select count(*) from outbox_events where aggregate_id = ?::uuid and event_type = 'account.status-changed'",
                    Integer.class, accountId);
            assertThat(statusEvents).isEqualTo(3);
        }

        @Test
        void aReasonIsMandatory() throws Exception {
            TestUser manager = TestUser.manager("BR001");
            String accountId = openAccount(manager, "BR001");

            send(post("/api/v1/accounts/" + accountId + "/freeze"), manager, "{\"reason\":\"   \"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void accountsOutsideTheCallersBranchCannotBeChanged() throws Exception {
            String accountId = openAccount(TestUser.admin(), "BR002");

            send(post("/api/v1/accounts/" + accountId + "/freeze"), TestUser.manager("BR001"), "{\"reason\":\"x\"}")
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------- Balance read-model

    @Nested
    class BalanceSnapshots {

        @Test
        void snapshotsUpdateBalancesAndStaleOnesAreIgnored() throws Exception {
            TestUser manager = TestUser.manager("BR001");
            String accountId = openAccount(manager, "BR001");
            UUID id = UUID.fromString(accountId);

            accountService.applyBalanceSnapshot(new ApplyBalanceSnapshotRequest(id, new BigDecimal("150.75"), new BigDecimal("100.25"), 2L));
            accountService.applyBalanceSnapshot(new ApplyBalanceSnapshotRequest(id, new BigDecimal("999.00"), new BigDecimal("999.00"), 2L));
            accountService.applyBalanceSnapshot(new ApplyBalanceSnapshotRequest(id, new BigDecimal("1.00"), new BigDecimal("1.00"), 1L));

            send(get("/api/v1/accounts/" + accountId + "/balance"), manager)
                    .andExpect(jsonPath("$.ledgerBalance").value(150.75))
                    .andExpect(jsonPath("$.availableBalance").value(100.25));
            send(post("/api/v1/accounts/" + accountId + "/close"), manager, "{\"reason\":\"has funds\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

            accountService.applyBalanceSnapshot(new ApplyBalanceSnapshotRequest(id, BigDecimal.ZERO, BigDecimal.ZERO, 3L));
            send(post("/api/v1/accounts/" + accountId + "/close"), manager, "{\"reason\":\"withdrawn\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        @Test
        void unknownAccountSnapshotIsNotFound() {
            org.junit.jupiter.api.Assertions.assertThrows(org.bank.common.exception.ResourceNotFoundException.class,
                    () -> accountService.applyBalanceSnapshot(
                            new ApplyBalanceSnapshotRequest(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ONE, 1L)));
        }
    }

    // ---------------------------------------------------------------- Schema guarantees

    @Nested
    class Schema {

        @Test
        void theDatabaseRejectsNegativeBalancesAndInconsistentClosure() throws Exception {
            String accountId = openAccount(TestUser.admin(), "BR001");

            org.junit.jupiter.api.Assertions.assertAll(
                    () -> org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                            () -> jdbc.update("update accounts set ledger_balance = -1 where id = ?::uuid", accountId)),
                    () -> org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                            () -> jdbc.update("update accounts set status = 'CLOSED' where id = ?::uuid", accountId)),
                    () -> org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                            () -> jdbc.update("update accounts set currency = 'eg' where id = ?::uuid", accountId)));
        }
    }

    // ---------------------------------------------------------------- Query budget

    @Nested
    class QueryCounts {

        @Test
        void readsAndWritesStayWithinTheirQueryBudget() throws Exception {
            TestUser manager = TestUser.manager("BR001");
            UUID customerId = UUID.randomUUID();
            for (int i = 0; i < 3; i++) {
                open(manager, customerId, "BR001");
            }
            String accountId = openAccount(manager, "BR001");
            // Warm the product catalogue cache so counts reflect steady state.
            send(get("/api/v1/account-products"), manager).andExpect(status().isOk());

            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

            stats.clear();
            send(get("/api/v1/accounts/" + accountId), manager).andExpect(status().isOk());
            assertThat(stats.getPrepareStatementCount()).as("get").isEqualTo(1);

            stats.clear();
            send(get("/api/v1/accounts?customerId=" + customerId + "&size=20"), manager)
                    .andExpect(jsonPath("$.content", hasSize(3)));
            assertThat(stats.getPrepareStatementCount()).as("list (short page, no count query)").isEqualTo(1);

            stats.clear();
            send(get("/api/v1/accounts?customerId=" + customerId + "&size=2"), manager)
                    .andExpect(jsonPath("$.content", hasSize(2)));
            assertThat(stats.getPrepareStatementCount()).as("list (page + count)").isEqualTo(2);

            stats.clear();
            send(post("/api/v1/accounts/" + accountId + "/freeze"), manager, "{\"reason\":\"budget\"}").andExpect(status().isOk());
            assertThat(stats.getPrepareStatementCount()).as("freeze: select + update + history + outbox").isLessThanOrEqualTo(4);
        }
    }

    // ---------------------------------------------------------------- helpers

    private String openAccount(TestUser caller, String branch) throws Exception {
        return JsonPath.read(open(caller, UUID.randomUUID(), branch), "$.id");
    }

    private String open(TestUser caller, UUID customerId, String branch) throws Exception {
        return send(post("/api/v1/accounts"), caller, openBody(customerId, "SAVINGS_STD", "EGP", branch))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String openBody(UUID customerId, String productCode, String currency, String branch) {
        return "{\"customerId\":\"" + customerId + "\",\"productCode\":\"" + productCode + "\",\"currency\":\""
                + currency + "\",\"branchCode\":\"" + branch + "\"}";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, TestUser caller) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + identity.tokenFor(caller)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, TestUser caller, String json) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + identity.tokenFor(caller))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static MockHttpServletRequestBuilder get(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url);
    }
}
