package org.bank.ledger;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.bank.ledger.support.TestIdentity;
import org.bank.ledger.support.TestUser;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the Ledger service against a real PostgreSQL (Flyway schema, constraints, triggers, row
 * locking), with requests authenticated by tokens in the format Identity issues ({@link TestIdentity} plays
 * Identity). Skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerApiIntegrationTest {

    /** Configured in application-test.yml as a system (bank-owned) account. */
    private static final UUID CASH = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final String BATCHES = "/api/v1/ledger/journal-batches";

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

    private final TestIdentity identity = TestIdentity.get();
    private final TestUser manager = TestUser.manager("BR001");

    // ---------------------------------------------------------------- Authentication and authorisation

    @Nested
    class Security {

        @Test
        void callersWithoutAValidTokenAreRejected() throws Exception {
            mvc.perform(MockMvcRequestBuilders.get("/api/v1/ledger/accounts/" + UUID.randomUUID() + "/balance"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(MockMvcRequestBuilders.post(BATCHES).header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + identity.forgedTokenFor(manager))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tellersAndAuditorsCannotPost() throws Exception {
            UUID customer = UUID.randomUUID();
            String body = batch(newRef(), entry(CASH, "DEBIT", "10.00", "EGP"), entry(customer, "CREDIT", "10.00", "EGP"));

            send(post(BATCHES), TestUser.teller("BR001"), body).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            send(post(BATCHES), TestUser.auditor(), body).andExpect(status().isForbidden());
        }

        @Test
        void tellersCannotReconcileButAuditorsCan() throws Exception {
            UUID customer = fundedCustomer("50.00");

            send(get("/api/v1/ledger/accounts/" + customer + "/reconciliation"), TestUser.teller("BR001"))
                    .andExpect(status().isForbidden());
            send(get("/api/v1/ledger/accounts/" + customer + "/reconciliation"), TestUser.auditor())
                    .andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------- Posting

    @Nested
    class Posting {

        @Test
        void aDepositCreditsTheCustomerDebitsCashAndWritesOutboxEvents() throws Exception {
            UUID customer = UUID.randomUUID();
            String ref = newRef();

            String response = send(post(BATCHES), manager,
                    batch(ref, entry(CASH, "DEBIT", "100.00", "EGP"), entry(customer, "CREDIT", "100.00", "EGP", "Cash deposit")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", startsWith("http://localhost" + BATCHES + "/")))
                    .andExpect(jsonPath("$.batchRef").value(ref))
                    .andExpect(jsonPath("$.status").value("POSTED"))
                    .andExpect(jsonPath("$.entries", hasSize(2)))
                    .andExpect(jsonPath("$.entries[0].lineNo").value(1))
                    .andExpect(jsonPath("$.entries[1].reference").value("Cash deposit"))
                    .andReturn().getResponse().getContentAsString();

            send(get("/api/v1/ledger/accounts/" + customer + "/balance"), manager)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountKind").value("CUSTOMER"))
                    .andExpect(jsonPath("$.ledgerBalance").value(100.0))
                    .andExpect(jsonPath("$.availableBalance").value(100.0))
                    .andExpect(jsonPath("$.balanceVersion").value(1));
            send(get("/api/v1/ledger/accounts/" + CASH + "/balance"), manager)
                    .andExpect(jsonPath("$.accountKind").value("SYSTEM"));

            String batchId = JsonPath.read(response, "$.id");
            send(get(BATCHES + "/" + batchId), manager)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.entries", hasSize(2)));

            // One snapshot for the customer in the Account contract's shape; none for the bank's own cash account.
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?::uuid and event_type = 'ledger.balance-updated'",
                    Integer.class, customer.toString())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?::uuid",
                    Integer.class, CASH.toString())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?::uuid and event_type = 'ledger.batch-posted'",
                    Integer.class, batchId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select payload ->> 'balanceVersion' from outbox_events where aggregate_id = ?::uuid and event_type = 'ledger.balance-updated'",
                    String.class, customer.toString())).isEqualTo("1");
        }

        @Test
        void balanceVersionIncreasesWithEveryPosting() throws Exception {
            UUID customer = fundedCustomer("100.00");
            withdraw(customer, "30.00").andExpect(status().isCreated());

            send(get("/api/v1/ledger/accounts/" + customer + "/balance"), manager)
                    .andExpect(jsonPath("$.ledgerBalance").value(70.0))
                    .andExpect(jsonPath("$.balanceVersion").value(2));
        }

        @Test
        void unbalancedBatchesAreRejectedAndLeaveNothingBehind() throws Exception {
            UUID customer = UUID.randomUUID();
            String ref = newRef();

            send(post(BATCHES), manager, batch(ref, entry(CASH, "DEBIT", "100.00", "EGP"), entry(customer, "CREDIT", "99.00", "EGP")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

            assertThat(count("journal_batches", ref)).isZero();
            send(get("/api/v1/ledger/accounts/" + customer + "/balance"), manager).andExpect(status().isNotFound());
        }

        @Test
        void customerAccountsCannotBeOverdrawnAndTheWholeBatchRollsBack() throws Exception {
            UUID customer = fundedCustomer("50.00");
            String ref = newRef();

            send(post(BATCHES), manager, batch(ref, entry(customer, "DEBIT", "50.01", "EGP"), entry(CASH, "CREDIT", "50.01", "EGP")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient funds")));

            assertThat(count("journal_batches", ref)).isZero();
            send(get("/api/v1/ledger/accounts/" + customer + "/balance"), manager)
                    .andExpect(jsonPath("$.ledgerBalance").value(50.0))
                    .andExpect(jsonPath("$.balanceVersion").value(1));
        }

        @Test
        void aBrandNewCustomerAccountCannotStartWithADebit() throws Exception {
            UUID customer = UUID.randomUUID();

            withdraw(customer, "1.00").andExpect(status().isUnprocessableEntity());
            send(get("/api/v1/ledger/accounts/" + customer + "/balance"), manager).andExpect(status().isNotFound());
        }

        @Test
        void systemAccountsMayGoNegative() throws Exception {
            UUID systemAccount = UUID.fromString("00000000-0000-7000-8000-000000000002");
            UUID customer = UUID.randomUUID();

            send(post(BATCHES), manager, batch(newRef(), entry(systemAccount, "CREDIT", "5.00", "EGP"), entry(customer, "DEBIT", "5.00", "EGP")))
                    .andExpect(status().isUnprocessableEntity()); // the customer side still cannot be overdrawn
            send(post(BATCHES), manager, batch(newRef(), entry(customer, "CREDIT", "5.00", "EGP"), entry(systemAccount, "DEBIT", "5.00", "EGP")))
                    .andExpect(status().isCreated());
        }

        @Test
        void anAccountKeepsItsCurrency() throws Exception {
            UUID customer = fundedCustomer("10.00");

            send(post(BATCHES), manager, batch(newRef(), entry(CASH, "DEBIT", "1.00", "USD"), entry(customer, "CREDIT", "1.00", "USD")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EGP")));
        }

        @Test
        void invalidRequestsAreRejectedWithFieldViolations() throws Exception {
            UUID customer = UUID.randomUUID();

            // One entry only, a non-positive amount, three decimals and a lower-case currency.
            send(post(BATCHES), manager, batch(newRef(), entry(customer, "CREDIT", "10.00", "EGP")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            send(post(BATCHES), manager, batch(newRef(), entry(CASH, "DEBIT", "0", "EGP"), entry(customer, "CREDIT", "0", "EGP")))
                    .andExpect(status().isUnprocessableEntity());
            send(post(BATCHES), manager, batch(newRef(), entry(CASH, "DEBIT", "1.001", "EGP"), entry(customer, "CREDIT", "1.001", "EGP")))
                    .andExpect(status().isUnprocessableEntity());
            send(post(BATCHES), manager, batch(newRef(), entry(CASH, "DEBIT", "1.00", "egp"), entry(customer, "CREDIT", "1.00", "egp")))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ---------------------------------------------------------------- Idempotency

    @Nested
    class Idempotency {

        @Test
        void replayingTheSameBatchReturnsTheOriginalWithoutPostingAgain() throws Exception {
            UUID customer = UUID.randomUUID();
            String ref = newRef();
            String body = batch(ref, entry(CASH, "DEBIT", "20.00", "EGP"), entry(customer, "CREDIT", "20.00", "EGP"));

            String first = send(post(BATCHES), manager, body).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            send(post(BATCHES), manager, body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")))
                    .andExpect(jsonPath("$.entries", hasSize(2)));

            assertThat(count("journal_batches", ref)).isEqualTo(1);
            send(get("/api/v1/ledger/accounts/" + customer + "/balance"), manager)
                    .andExpect(jsonPath("$.ledgerBalance").value(20.0))
                    .andExpect(jsonPath("$.balanceVersion").value(1));
        }

        @Test
        void reusingAReferenceWithDifferentContentIsAConflict() throws Exception {
            UUID customer = UUID.randomUUID();
            String ref = newRef();
            send(post(BATCHES), manager, batch(ref, entry(CASH, "DEBIT", "20.00", "EGP"), entry(customer, "CREDIT", "20.00", "EGP")))
                    .andExpect(status().isCreated());

            send(post(BATCHES), manager, batch(ref, entry(CASH, "DEBIT", "21.00", "EGP"), entry(customer, "CREDIT", "21.00", "EGP")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
        }
    }

    // ---------------------------------------------------------------- Statements and reconciliation

    @Nested
    class Statements {

        @Test
        void statementIsNewestFirstAndFilterable() throws Exception {
            UUID customer = fundedCustomer("100.00");
            withdraw(customer, "10.00").andExpect(status().isCreated());
            withdraw(customer, "5.00").andExpect(status().isCreated());

            send(get("/api/v1/ledger/accounts/" + customer + "/entries"), manager)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.content[0].amount").value(5.0))
                    .andExpect(jsonPath("$.content[0].entryType").value("DEBIT"))
                    .andExpect(jsonPath("$.content[2].entryType").value("CREDIT"));
            send(get("/api/v1/ledger/accounts/" + customer + "/entries?entryType=CREDIT"), manager)
                    .andExpect(jsonPath("$.totalElements").value(1));
            send(get("/api/v1/ledger/accounts/" + customer + "/entries?sort=amount,asc&size=1"), manager)
                    .andExpect(jsonPath("$.content[0].amount").value(5.0))
                    .andExpect(jsonPath("$.totalPages").value(3));
            send(get("/api/v1/ledger/accounts/" + customer + "/entries?from=2999-01-01T00:00:00Z"), manager)
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void unknownAccountsAndBadSortsAreRejected() throws Exception {
            send(get("/api/v1/ledger/accounts/" + UUID.randomUUID() + "/entries"), manager)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            send(get("/api/v1/ledger/accounts/" + fundedCustomer("1.00") + "/entries?sort=accountId"), manager)
                    .andExpect(status().isBadRequest());
            send(get(BATCHES + "/" + UUID.randomUUID()), manager).andExpect(status().isNotFound());
        }

        @Test
        void reconciliationMatchesAfterPostingsAndDetectsTampering() throws Exception {
            UUID customer = fundedCustomer("100.00");
            withdraw(customer, "30.00").andExpect(status().isCreated());

            send(get("/api/v1/ledger/accounts/" + customer + "/reconciliation"), manager)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consistent").value(true))
                    .andExpect(jsonPath("$.storedBalance").value(70.0))
                    .andExpect(jsonPath("$.computedBalance").value(70.0))
                    .andExpect(jsonPath("$.discrepancy").value(0.0));

            // Someone edits a balance behind the application's back: reconciliation must notice.
            jdbc.update("update account_balances set ledger_balance = ledger_balance + 1 where account_id = ?::uuid", customer.toString());
            send(get("/api/v1/ledger/accounts/" + customer + "/reconciliation"), manager)
                    .andExpect(jsonPath("$.consistent").value(false))
                    .andExpect(jsonPath("$.discrepancy").value(1.0));
        }
    }

    // ---------------------------------------------------------------- Database guarantees

    @Nested
    class Database {

        @Test
        void journalHistoryIsAppendOnly() throws Exception {
            fundedCustomer("10.00");

            assertThatThrownBy(() -> jdbc.update("update ledger_entries set amount = amount + 1"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.update("delete from ledger_entries"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.execute("truncate table ledger_entries"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.update("update journal_batches set status = 'POSTED'"))
                    .hasMessageContaining("append-only");
        }

        @Test
        void theDatabaseItselfRefusesANegativeCustomerBalance() {
            UUID id = UUID.randomUUID();
            assertThatThrownBy(() -> jdbc.update("""
                    insert into account_balances (id, account_id, account_kind, currency, ledger_balance, available_balance)
                    values (?::uuid, ?::uuid, 'CUSTOMER', 'EGP', -1, -1)
                    """, id.toString(), id.toString())).hasMessageContaining("account_balances_customer_chk");
        }
    }

    // ---------------------------------------------------------------- Concurrency and performance

    @Nested
    class Concurrency {

        @Test
        void concurrentWithdrawalsNeverOverdrawTheAccount() throws Exception {
            UUID customer = fundedCustomer("1000.00");

            List<Integer> statuses = runConcurrently(20, i -> () -> withdraw(customer, "100.00")
                    .andReturn().getResponse().getStatus());

            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(10);
            assertThat(statuses.stream().filter(s -> s == 422).count()).isEqualTo(10);
            send(get("/api/v1/ledger/accounts/" + customer + "/balance"), manager)
                    .andExpect(jsonPath("$.ledgerBalance").value(0.0))
                    .andExpect(jsonPath("$.balanceVersion").value(11));
            send(get("/api/v1/ledger/accounts/" + customer + "/reconciliation"), manager)
                    .andExpect(jsonPath("$.consistent").value(true));
        }

        @Test
        void opposingTransfersBetweenTheSameAccountsDoNotDeadlock() throws Exception {
            UUID a = fundedCustomer("1000.00");
            UUID b = fundedCustomer("1000.00");

            List<Integer> statuses = runConcurrently(20, i -> () -> {
                UUID from = i % 2 == 0 ? a : b;
                UUID to = i % 2 == 0 ? b : a;
                return send(post(BATCHES), manager,
                        batch(newRef(), entry(from, "DEBIT", "1.00", "EGP"), entry(to, "CREDIT", "1.00", "EGP")))
                        .andReturn().getResponse().getStatus();
            });

            assertThat(statuses).allMatch(s -> s == 201);
            for (UUID account : List.of(a, b)) {
                send(get("/api/v1/ledger/accounts/" + account + "/reconciliation"), manager)
                        .andExpect(jsonPath("$.consistent").value(true))
                        .andExpect(jsonPath("$.storedBalance").value(1000.0));
            }
        }

        @Test
        void aPostingToExistingAccountsNeedsAtMostEightStatements() throws Exception {
            UUID customer = fundedCustomer("100.00");
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();

            withdraw(customer, "10.00").andExpect(status().isCreated());

            // idempotency lookup + batch + 2 balance updates + balance read-back + entries + outbox rows
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(8);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Runs the tasks on a small pool, all released together, and returns their results in task order. */
    private <T> List<T> runConcurrently(int tasks, java.util.function.IntFunction<Callable<T>> factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                futures.add(pool.submit(factory.apply(i)));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID fundedCustomer(String amount) throws Exception {
        UUID customer = UUID.randomUUID();
        send(post(BATCHES), manager, batch(newRef(), entry(CASH, "DEBIT", amount, "EGP"), entry(customer, "CREDIT", amount, "EGP")))
                .andExpect(status().isCreated());
        return customer;
    }

    private ResultActions withdraw(UUID customer, String amount) throws Exception {
        return send(post(BATCHES), manager, batch(newRef(), entry(customer, "DEBIT", amount, "EGP"), entry(CASH, "CREDIT", amount, "EGP")));
    }

    private int count(String table, String batchRef) {
        return jdbc.queryForObject("select count(*) from " + table + " where batch_ref = ?", Integer.class, batchRef);
    }

    private static String newRef() {
        return "B-" + UUID.randomUUID();
    }

    private static String entry(UUID account, String type, String amount, String currency) {
        return entry(account, type, amount, currency, null);
    }

    private static String entry(UUID account, String type, String amount, String currency, String reference) {
        return "{\"accountId\":\"" + account + "\",\"entryType\":\"" + type + "\",\"amount\":" + amount
                + ",\"currency\":\"" + currency + "\""
                + (reference == null ? "" : ",\"reference\":\"" + reference + "\"") + "}";
    }

    private static String batch(String ref, String... entries) {
        return "{\"batchRef\":\"" + ref + "\",\"sourceService\":\"transaction-service\",\"sourceEvent\":\"test\",\"entries\":["
                + String.join(",", entries) + "]}";
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
