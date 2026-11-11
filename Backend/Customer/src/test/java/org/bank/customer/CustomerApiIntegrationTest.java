package org.bank.customer;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.bank.customer.support.TestIdentity;
import org.bank.customer.support.TestUser;
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

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the Customer service against a real PostgreSQL (Flyway schema, constraints, indexes),
 * with requests authenticated by tokens in the format Identity issues. {@link TestIdentity} plays Identity:
 * it signs the tokens and publishes the public key over HTTP, so the whole production validation path of the
 * shared security module (JWKS fetch, RS256 signature, issuer, expiry, branch scoping) is exercised.
 * Skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerApiIntegrationTest {

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

    // ---------------------------------------------------------------- Identity tokens are accepted / refused

    @Nested
    class TokensIssuedByIdentity {

        @Test
        void aValidIdentityTokenAuthenticatesTheCaller() throws Exception {
            send(get("/api/v1/customers"), TestUser.teller("BR001")).andExpect(status().isOk());
        }

        @Test
        void callersWithoutATokenAreRejected() throws Exception {
            mvc.perform(get("/api/v1/customers"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(get("/api/v1/customers").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        void expiredTokensAreRejected() throws Exception {
            mvc.perform(get("/api/v1/customers").header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + identity.expiredTokenFor(TestUser.teller("BR001"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tokensFromAnotherIssuerAreRejected() throws Exception {
            mvc.perform(get("/api/v1/customers").header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + identity.tokenFromOtherIssuer(TestUser.admin())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tokensSignedWithAKeyIdentityNeverPublishedAreRejected() throws Exception {
            mvc.perform(get("/api/v1/customers").header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + identity.forgedTokenFor(TestUser.admin())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void aValidTokenWithoutTheRequiredPermissionIsForbidden() throws Exception {
            TestUser branchViewerOnly = TestUser.teller("BR001").withPermissions(Set.of("BRANCH_VIEW"));

            send(get("/api/v1/customers"), branchViewerOnly)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        void tellersCannotUseManagerOnlyFunctions() throws Exception {
            String customerId = registerCustomer(TestUser.admin(), "BR001", tag());

            send(patch("/api/v1/customers/" + customerId + "/status"), TestUser.teller("BR001"),
                    "{\"status\":\"BLOCKED\",\"reason\":\"test\"}").andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- Branch scoping

    @Nested
    class BranchScoping {

        @Test
        void callersOnlySeeCustomersOfTheirOwnBranch() throws Exception {
            String tag = tag();
            registerCustomer(TestUser.admin(), "BR001", tag + " in-br001");
            registerCustomer(TestUser.admin(), "BR002", tag + " in-br002");

            send(get("/api/v1/customers?q=" + tag), TestUser.teller("BR001"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].branchCode").value("BR001"));
            send(get("/api/v1/customers?q=" + tag), TestUser.teller("BR002"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].branchCode").value("BR002"));
        }

        @Test
        void crossBranchRolesSeeEveryBranch() throws Exception {
            String tag = tag();
            registerCustomer(TestUser.admin(), "BR001", tag + " in-br001");
            registerCustomer(TestUser.admin(), "BR002", tag + " in-br002");

            send(get("/api/v1/customers?q=" + tag), TestUser.admin()).andExpect(jsonPath("$.totalElements").value(2));
            send(get("/api/v1/customers?q=" + tag), TestUser.auditor()).andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void aBranchFilterCannotWidenARestrictedCallersScope() throws Exception {
            String tag = tag();
            registerCustomer(TestUser.admin(), "BR001", tag + " in-br001");
            registerCustomer(TestUser.admin(), "BR002", tag + " in-br002");

            send(get("/api/v1/customers?branchCode=BR002&q=" + tag), TestUser.teller("BR001"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].branchCode").value("BR001"));
        }

        @Test
        void aCustomerOfAnotherBranchIsReportedAsNotFound() throws Exception {
            String otherBranchCustomer = registerCustomer(TestUser.admin(), "BR002", tag());

            send(get("/api/v1/customers/" + otherBranchCustomer), TestUser.teller("BR001"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            send(get("/api/v1/customers/" + otherBranchCustomer + "/kyc"), TestUser.teller("BR001"))
                    .andExpect(status().isNotFound());
            send(patch("/api/v1/customers/" + otherBranchCustomer + "/status"), TestUser.manager("BR001"),
                    "{\"status\":\"BLOCKED\",\"reason\":\"probe\"}").andExpect(status().isNotFound());
        }

        @Test
        void tellersCannotRegisterCustomersInAnotherBranch() throws Exception {
            send(post("/api/v1/customers"), TestUser.teller("BR001"),
                    registerJson(tag(), uniqueNationalId(), "BR002")).andExpect(status().isForbidden());
        }

        @Test
        void branchListsAreScopedToo() throws Exception {
            send(get("/api/v1/branches"), TestUser.teller("BR001"))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].code").value("BR001"));
            send(get("/api/v1/branches/BR002"), TestUser.teller("BR001")).andExpect(status().isNotFound());
            send(get("/api/v1/branches/BR002"), TestUser.admin()).andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------- Branches

    @Nested
    class Branches {

        @Test
        void adminsManageBranchesAndTellersCannot() throws Exception {
            String code = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String body = "{\"code\":\"" + code.toLowerCase() + "\",\"name\":\"Test Branch\",\"region\":\"Giza\"}";

            send(post("/api/v1/branches"), TestUser.teller("BR001"), body).andExpect(status().isForbidden());
            send(post("/api/v1/branches"), TestUser.admin(), body)
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/branches/" + code)))
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
            send(post("/api/v1/branches"), TestUser.admin(), body)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
            send(patch("/api/v1/branches/" + code), TestUser.admin(), "{\"name\":\"Renamed\"}")
                    .andExpect(jsonPath("$.name").value("Renamed"))
                    .andExpect(jsonPath("$.region").value("Giza"));
        }

        @Test
        void customersCannotBeRegisteredAtAnInactiveOrUnknownBranch() throws Exception {
            String code = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            send(post("/api/v1/branches"), TestUser.admin(), "{\"code\":\"" + code + "\",\"name\":\"Closing Branch\"}")
                    .andExpect(status().isCreated());
            // Registering works while the branch is active (this also fills the branch cache) ...
            registerCustomer(TestUser.admin(), code, tag());

            send(patch("/api/v1/branches/" + code), TestUser.admin(), "{\"status\":\"INACTIVE\"}").andExpect(status().isOk());

            // ... and stops right after deactivation: the cache entry was evicted when the change committed.
            send(post("/api/v1/customers"), TestUser.admin(), registerJson(tag(), uniqueNationalId(), code))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
            send(post("/api/v1/customers"), TestUser.admin(), registerJson(tag(), uniqueNationalId(), "NOPE"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ---------------------------------------------------------------- Customers

    @Nested
    class Customers {

        @Test
        void registrationReturnsMaskedNationalIdAndNeverStoresTheRealOne() throws Exception {
            String nationalId = uniqueNationalId();

            String body = send(post("/api/v1/customers"), TestUser.teller("BR001"), registerJson(tag(), nationalId, "BR001"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/customers/")))
                    .andExpect(jsonPath("$.customerNo", startsWith("CUS")))
                    .andExpect(jsonPath("$.nationalId").value("****" + nationalId.substring(nationalId.length() - 4)))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                    .andExpect(jsonPath("$.branchName").value("Downtown Branch"))
                    .andExpect(content().string(not(containsString(nationalId))))
                    .andReturn().getResponse().getContentAsString();

            UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
            String storedFingerprint = jdbc.queryForObject(
                    "select national_id_fingerprint from customers where id = ?", String.class, id);
            UUID createdBy = jdbc.queryForObject("select created_by from customers where id = ?", UUID.class, id);
            assertThat(storedFingerprint).hasSize(64).doesNotContain(nationalId);
            assertThat(createdBy).as("audit: created_by is the token subject").isNotNull();
        }

        @Test
        void theSameNationalIdCannotBeRegisteredTwiceHoweverItIsFormatted() throws Exception {
            String nationalId = uniqueNationalId();
            send(post("/api/v1/customers"), TestUser.admin(), registerJson(tag(), nationalId, "BR001"))
                    .andExpect(status().isCreated());

            send(post("/api/v1/customers"), TestUser.admin(), registerJson(tag(), nationalId.toLowerCase(), "BR002"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
        }

        @Test
        void invalidRequestsReturnFieldViolations() throws Exception {
            send(post("/api/v1/customers"), TestUser.teller("BR001"), "{\"branchCode\":\"BR001\",\"nationalId\":\"x\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations").isNotEmpty());
        }

        @Test
        void profileUpdatesArePartialAndRiskLevelNeedsExtraAuthority() throws Exception {
            String id = registerCustomer(TestUser.teller("BR001"), "BR001", tag());

            send(patch("/api/v1/customers/" + id), TestUser.teller("BR001"), "{\"fullName\":\"Renamed Person\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Renamed Person"))
                    .andExpect(jsonPath("$.riskLevel").value("LOW"));
            send(patch("/api/v1/customers/" + id), TestUser.teller("BR001"), "{\"riskLevel\":\"HIGH\"}")
                    .andExpect(status().isForbidden());
            send(patch("/api/v1/customers/" + id), TestUser.manager("BR001"), "{\"riskLevel\":\"HIGH\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.riskLevel").value("HIGH"));
        }

        @Test
        void statusChangesNeedAReasonFollowTheLifecycleAndLeaveAnAuditTrail() throws Exception {
            String id = registerCustomer(TestUser.admin(), "BR001", tag());
            TestUser manager = TestUser.manager("BR001");

            send(patch("/api/v1/customers/" + id + "/status"), manager, "{\"status\":\"BLOCKED\"}")
                    .andExpect(status().isUnprocessableEntity());
            send(patch("/api/v1/customers/" + id + "/status"), manager, "{\"status\":\"BLOCKED\",\"reason\":\"Suspicious activity\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("BLOCKED"));
            send(patch("/api/v1/customers/" + id + "/status"), manager, "{\"status\":\"CLOSED\",\"reason\":\"Requested by customer\"}")
                    .andExpect(status().isOk());
            send(patch("/api/v1/customers/" + id + "/status"), manager, "{\"status\":\"ACTIVE\",\"reason\":\"Reopen\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

            Integer historyRows = jdbc.queryForObject(
                    "select count(*) from customer_status_history where customer_id = ?", Integer.class, UUID.fromString(id));
            Integer statusEvents = jdbc.queryForObject(
                    "select count(*) from outbox_events where aggregate_id = ? and event_type = 'customer.status-changed'",
                    Integer.class, UUID.fromString(id));
            assertThat(historyRows).isEqualTo(2);
            assertThat(statusEvents).isEqualTo(2);
        }

        @Test
        void registrationWritesAnOutboxEventWithoutPersonalData() throws Exception {
            String name = tag() + " Sensitive Name";
            String id = registerCustomer(TestUser.admin(), "BR001", name);

            String payload = jdbc.queryForObject(
                    "select payload::text from outbox_events where aggregate_id = ? and event_type = 'customer.registered'",
                    String.class, UUID.fromString(id));
            assertThat(payload).contains(id, "BR001").doesNotContain("Sensitive Name");
        }

        @Test
        void listingRejectsUnknownSortFieldsAndCapsThePageSize() throws Exception {
            send(get("/api/v1/customers?sort=nationalIdFingerprint"), TestUser.admin())
                    .andExpect(status().isBadRequest());
            send(get("/api/v1/customers?size=100000"), TestUser.admin())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }
    }

    // ---------------------------------------------------------------- KYC (maker-checker)

    @Nested
    class Kyc {

        @Test
        void aDifferentCheckerApprovesIdentityEvidenceAndTheCustomerBecomesVerified() throws Exception {
            String customerId = registerCustomer(TestUser.teller("BR001"), "BR001", tag());
            String recordId = submitKyc(TestUser.teller("BR001"), customerId, "NATIONAL_ID");

            send(post(reviewUrl(customerId, recordId)), TestUser.manager("BR001"), "{\"decision\":\"APPROVED\",\"notes\":\"Original seen\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            send(get("/api/v1/customers/" + customerId), TestUser.teller("BR001"))
                    .andExpect(jsonPath("$.kycStatus").value("VERIFIED"))
                    .andExpect(jsonPath("$.kycRecords", hasSize(1)));
        }

        @Test
        void tellersCannotReviewEvidence() throws Exception {
            String customerId = registerCustomer(TestUser.teller("BR001"), "BR001", tag());
            String recordId = submitKyc(TestUser.teller("BR001"), customerId, "PASSPORT");

            send(post(reviewUrl(customerId, recordId)), TestUser.teller("BR001"), "{\"decision\":\"APPROVED\"}")
                    .andExpect(status().isForbidden());
        }

        @Test
        void theSubmitterCannotReviewTheirOwnEvidenceEvenWithBothPermissions() throws Exception {
            TestUser makerAndChecker = TestUser.manager("BR001").withPermissions(
                    Set.of("CUSTOMER_VIEW", "CUSTOMER_CREATE", "KYC_SUBMIT", "KYC_REVIEW"));
            String customerId = registerCustomer(makerAndChecker, "BR001", tag());
            String recordId = submitKyc(makerAndChecker, customerId, "NATIONAL_ID");

            send(post(reviewUrl(customerId, recordId)), makerAndChecker, "{\"decision\":\"APPROVED\"}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        }

        @Test
        void decisionsAreFinalAndRejectionNeedsAReason() throws Exception {
            String customerId = registerCustomer(TestUser.teller("BR001"), "BR001", tag());
            String recordId = submitKyc(TestUser.teller("BR001"), customerId, "NATIONAL_ID");
            TestUser checker = TestUser.manager("BR001");

            send(post(reviewUrl(customerId, recordId)), checker, "{\"decision\":\"REJECTED\"}")
                    .andExpect(status().isUnprocessableEntity());
            send(post(reviewUrl(customerId, recordId)), checker, "{\"decision\":\"REJECTED\",\"notes\":\"Scan is unreadable\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
            send(post(reviewUrl(customerId, recordId)), checker, "{\"decision\":\"APPROVED\"}")
                    .andExpect(status().isUnprocessableEntity());

            send(get("/api/v1/customers/" + customerId), checker).andExpect(jsonPath("$.kycStatus").value("REJECTED"));
        }

        @Test
        void onlyOneOpenSubmissionPerDocumentTypeIsAllowed() throws Exception {
            String customerId = registerCustomer(TestUser.teller("BR001"), "BR001", tag());
            submitKyc(TestUser.teller("BR001"), customerId, "NATIONAL_ID");

            send(post("/api/v1/customers/" + customerId + "/kyc"), TestUser.teller("BR001"),
                    "{\"documentType\":\"NATIONAL_ID\",\"documentRef\":\"SCAN-2\"}")
                    .andExpect(status().isConflict());
            // another document type is fine
            submitKyc(TestUser.teller("BR001"), customerId, "UTILITY_BILL");
            send(get("/api/v1/customers/" + customerId + "/kyc"), TestUser.teller("BR001"))
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void reviewersOfAnotherBranchCannotSeeTheEvidence() throws Exception {
            String customerId = registerCustomer(TestUser.teller("BR001"), "BR001", tag());
            String recordId = submitKyc(TestUser.teller("BR001"), customerId, "NATIONAL_ID");

            send(post(reviewUrl(customerId, recordId)), TestUser.manager("BR002"), "{\"decision\":\"APPROVED\"}")
                    .andExpect(status().isNotFound());
        }

        @Test
        void closedCustomersCannotReceiveNewEvidence() throws Exception {
            String customerId = registerCustomer(TestUser.admin(), "BR001", tag());
            send(patch("/api/v1/customers/" + customerId + "/status"), TestUser.manager("BR001"),
                    "{\"status\":\"CLOSED\",\"reason\":\"Closed\"}").andExpect(status().isOk());

            send(post("/api/v1/customers/" + customerId + "/kyc"), TestUser.teller("BR001"),
                    "{\"documentType\":\"PASSPORT\",\"documentRef\":\"SCAN-1\"}")
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ---------------------------------------------------------------- No N+1

    @Nested
    class QueryCounts {

        @Test
        void listingRunsAFixedNumberOfQueriesWhateverThePageSize() throws Exception {
            String tag = tag();
            for (int i = 0; i < 30; i++) {
                registerCustomer(TestUser.admin(), "BR001", tag + " customer " + i);
            }
            TestUser teller = TestUser.teller("BR001");
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

            statistics.clear();
            send(get("/api/v1/customers?size=30&q=" + tag), teller).andExpect(jsonPath("$.content", hasSize(30)));
            long queriesForFullPage = statistics.getPrepareStatementCount();

            statistics.clear();
            send(get("/api/v1/customers?size=5&q=" + tag), teller).andExpect(jsonPath("$.content", hasSize(5)));
            long queriesForSmallPage = statistics.getPrepareStatementCount();

            assertThat(queriesForFullPage).as("page of 30 = one select + one count").isEqualTo(2);
            assertThat(queriesForSmallPage).as("page size must not change the query count").isEqualTo(queriesForFullPage);
        }

        @Test
        void aCustomerDetailRunsTwoQueries() throws Exception {
            TestUser teller = TestUser.teller("BR001");
            String customerId = registerCustomer(teller, "BR001", tag());
            submitKyc(teller, customerId, "NATIONAL_ID");
            submitKyc(teller, customerId, "UTILITY_BILL");
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            send(get("/api/v1/customers/" + customerId), teller); // warms the branch cache

            statistics.clear();
            send(get("/api/v1/customers/" + customerId), teller).andExpect(jsonPath("$.kycRecords", hasSize(2)));

            assertThat(statistics.getPrepareStatementCount()).as("customer + KYC records").isEqualTo(2);
        }

        @Test
        void kycReviewDoesNotQueryPerRecord() throws Exception {
            TestUser teller = TestUser.teller("BR001");
            String customerId = registerCustomer(teller, "BR001", tag());
            String recordId = submitKyc(teller, customerId, "NATIONAL_ID");
            for (String type : new String[]{"PASSPORT", "UTILITY_BILL", "PROOF_OF_INCOME", "DRIVING_LICENSE"}) {
                submitKyc(teller, customerId, type);
            }
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

            statistics.clear();
            send(post(reviewUrl(customerId, recordId)), TestUser.manager("BR001"), "{\"decision\":\"APPROVED\"}")
                    .andExpect(status().isOk());

            // customer, record, outcomes, update record, update customer, outbox insert: independent of the 5 records.
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueNationalId() {
        return "NID" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    private static String registerJson(String fullName, String nationalId, String branchCode) {
        return "{\"fullName\":\"" + fullName + "\",\"dateOfBirth\":\"1990-05-17\",\"nationalId\":\"" + nationalId
                + "\",\"branchCode\":\"" + branchCode + "\"}";
    }

    private static String reviewUrl(String customerId, String recordId) {
        return "/api/v1/customers/" + customerId + "/kyc/" + recordId + "/review";
    }

    private String registerCustomer(TestUser actor, String branchCode, String fullName) throws Exception {
        String body = send(post("/api/v1/customers"), actor, registerJson(fullName, uniqueNationalId(), branchCode))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String submitKyc(TestUser actor, String customerId, String documentType) throws Exception {
        String body = send(post("/api/v1/customers/" + customerId + "/kyc"), actor,
                "{\"documentType\":\"" + documentType + "\",\"documentRef\":\"SCAN-" + tag() + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, TestUser caller) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + identity.tokenFor(caller)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, TestUser caller, String jsonBody) throws Exception {
        return mvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + identity.tokenFor(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }

    private static MockHttpServletRequestBuilder get(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url);
    }

    private static MockHttpServletRequestBuilder patch(String url) {
        return MockMvcRequestBuilders.patch(url);
    }
}
