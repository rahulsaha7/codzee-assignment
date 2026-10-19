package io.settlementhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.settlementhub.common.web.PageResponse;
import io.settlementhub.merchant.dto.CreateMerchantRequest;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.security.user.dto.LoginRequest;
import io.settlementhub.security.user.dto.LoginResponse;
import io.settlementhub.settlement.SettlementBatchStatus;
import io.settlementhub.settlement.SettlementStatus;
import io.settlementhub.settlement.dto.CreateSettlementBatchRequest;
import io.settlementhub.settlement.dto.SettlementBatchResponse;
import io.settlementhub.settlement.dto.SettlementResponse;
import io.settlementhub.transaction.TransactionStatus;
import io.settlementhub.transaction.dto.CreateTransactionRequest;
import io.settlementhub.transaction.dto.TransactionResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class SettlementFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("settlement_hub")
                    .withUsername("settlement")
                    .withPassword("settlement");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private TestRestTemplate restTemplate;

    private HttpHeaders adminHeaders;

    @BeforeEach
    void authenticate() {
        LoginResponse login =
                restTemplate.postForObject(
                        "/api/v1/auth/login",
                        new LoginRequest("admin", "test-admin-password"),
                        LoginResponse.class);
        assertThat(login).isNotNull();
        assertThat(login.accessToken()).isNotBlank();
        adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(login.accessToken());
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void settlesAllMerchantsAndRepeatedRunsDoNotDoubleSettle() throws Exception {
        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC).minusDays(3);
        Instant capturedAt =
                businessDate.atStartOfDay(ZoneOffset.UTC).plusSeconds(3600).toInstant();

        MerchantResponse alpha = createMerchant("Alpha", 150);
        MerchantResponse beta = createMerchant("Beta", 250);
        capture(alpha.id(), "alpha-1", 10000, "USD", capturedAt);
        capture(alpha.id(), "alpha-2", 5000, "USD", capturedAt.plusSeconds(60));
        capture(beta.id(), "beta-1", 20000, "EUR", capturedAt);

        String idempotencyKey = "full-" + UUID.randomUUID();
        SettlementBatchResponse created = createBatch(businessDate, idempotencyKey);
        SettlementBatchResponse repeated = createBatch(businessDate, idempotencyKey);
        assertThat(repeated.id()).isEqualTo(created.id());

        SettlementBatchResponse completed = awaitTerminal(created.id());
        assertThat(completed.status()).isEqualTo(SettlementBatchStatus.COMPLETED);
        assertThat(completed.totalMerchants()).isEqualTo(2);
        assertThat(completed.processedMerchants()).isEqualTo(2);
        assertThat(completed.failedMerchants()).isZero();

        PageResponse<SettlementResponse> rows = settlements(created.id());
        assertThat(rows.items()).hasSize(2);

        SettlementResponse alphaSettlement = byMerchant(rows, alpha.id());
        assertThat(alphaSettlement.status()).isEqualTo(SettlementStatus.CREATED);
        assertThat(alphaSettlement.currency()).isEqualTo("USD");
        assertThat(alphaSettlement.grossMinor()).isEqualTo(15000L);
        assertThat(alphaSettlement.mdrMinor()).isEqualTo(225L);
        assertThat(alphaSettlement.netMinor()).isEqualTo(14775L);
        assertThat(alphaSettlement.txnCount()).isEqualTo(2);

        SettlementResponse betaSettlement = byMerchant(rows, beta.id());
        assertThat(betaSettlement.status()).isEqualTo(SettlementStatus.CREATED);
        assertThat(betaSettlement.currency()).isEqualTo("EUR");
        assertThat(betaSettlement.grossMinor()).isEqualTo(20000L);
        assertThat(betaSettlement.mdrMinor()).isEqualTo(500L);
        assertThat(betaSettlement.netMinor()).isEqualTo(19500L);
        assertThat(betaSettlement.txnCount()).isEqualTo(1);

        PageResponse<TransactionResponse> alphaTransactions = transactionsFor(alpha.id());
        assertThat(alphaTransactions.items()).hasSize(2);
        assertThat(alphaTransactions.items())
                .allSatisfy(
                        transaction -> {
                            assertThat(transaction.status()).isEqualTo(TransactionStatus.SETTLED);
                            assertThat(transaction.settlementId()).isEqualTo(alphaSettlement.id());
                        });

        PageResponse<TransactionResponse> betaTransactions = transactionsFor(beta.id());
        assertThat(betaTransactions.items()).hasSize(1);
        assertThat(betaTransactions.items().getFirst().status())
                .isEqualTo(TransactionStatus.SETTLED);
        assertThat(betaTransactions.items().getFirst().settlementId())
                .isEqualTo(betaSettlement.id());

        SettlementBatchResponse rerun = createBatch(businessDate, "rerun-" + UUID.randomUUID());
        SettlementBatchResponse rerunCompleted = awaitTerminal(rerun.id());
        assertThat(rerunCompleted.status()).isEqualTo(SettlementBatchStatus.COMPLETED);
        assertThat(rerunCompleted.totalMerchants()).isZero();
        assertThat(settlements(rerun.id()).items()).isEmpty();
    }

    @Test
    void recordsFailedSettlementAndContinuesOtherMerchants() throws Exception {
        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        Instant capturedAt =
                businessDate.atStartOfDay(ZoneOffset.UTC).plusSeconds(3600).toInstant();

        MerchantResponse mixed = createMerchant("Mixed", 100);
        MerchantResponse healthy = createMerchant("Healthy", 200);
        capture(mixed.id(), "mixed-usd", 1000, "USD", capturedAt);
        capture(mixed.id(), "mixed-eur", 1000, "EUR", capturedAt);
        capture(healthy.id(), "healthy-1", 5000, "USD", capturedAt);

        SettlementBatchResponse created = createBatch(businessDate, "partial-" + UUID.randomUUID());
        SettlementBatchResponse completed = awaitTerminal(created.id());

        assertThat(completed.status()).isEqualTo(SettlementBatchStatus.PARTIAL_FAILED);
        assertThat(completed.totalMerchants()).isEqualTo(2);
        assertThat(completed.processedMerchants()).isEqualTo(2);
        assertThat(completed.failedMerchants()).isEqualTo(1);

        PageResponse<SettlementResponse> rows = settlements(created.id());
        assertThat(rows.items()).hasSize(2);

        SettlementResponse mixedSettlement = byMerchant(rows, mixed.id());
        assertThat(mixedSettlement.status()).isEqualTo(SettlementStatus.FAILED);
        assertThat(mixedSettlement.failureReason()).contains("multiple currencies");

        SettlementResponse healthySettlement = byMerchant(rows, healthy.id());
        assertThat(healthySettlement.status()).isEqualTo(SettlementStatus.CREATED);
        assertThat(healthySettlement.grossMinor()).isEqualTo(5000L);
        assertThat(healthySettlement.mdrMinor()).isEqualTo(100L);
        assertThat(healthySettlement.netMinor()).isEqualTo(4900L);

        assertThat(transactionsFor(mixed.id()).items())
                .allSatisfy(
                        transaction ->
                                assertThat(transaction.status())
                                        .isEqualTo(TransactionStatus.CAPTURED));
        assertThat(transactionsFor(healthy.id()).items())
                .allSatisfy(
                        transaction -> {
                            assertThat(transaction.status()).isEqualTo(TransactionStatus.SETTLED);
                            assertThat(transaction.settlementId())
                                    .isEqualTo(healthySettlement.id());
                        });
    }

    private SettlementBatchResponse awaitTerminal(UUID batchId) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            SettlementBatchResponse batch = getBatch(batchId);
            if (batch.status() == SettlementBatchStatus.COMPLETED
                    || batch.status() == SettlementBatchStatus.PARTIAL_FAILED
                    || batch.status() == SettlementBatchStatus.FAILED) {
                return batch;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Settlement batch " + batchId + " did not reach a terminal state");
    }

    private SettlementBatchResponse createBatch(LocalDate businessDate, String idempotencyKey) {
        HttpHeaders headers = copyAdminHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        ResponseEntity<SettlementBatchResponse> response =
                restTemplate.exchange(
                        "/api/v1/settlements/batches",
                        HttpMethod.POST,
                        new HttpEntity<>(new CreateSettlementBatchRequest(businessDate), headers),
                        SettlementBatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private SettlementBatchResponse getBatch(UUID batchId) {
        ResponseEntity<SettlementBatchResponse> response =
                restTemplate.exchange(
                        "/api/v1/settlements/batches/" + batchId,
                        HttpMethod.GET,
                        new HttpEntity<>(adminHeaders),
                        SettlementBatchResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private PageResponse<SettlementResponse> settlements(UUID batchId) {
        ResponseEntity<PageResponse<SettlementResponse>> response =
                restTemplate.exchange(
                        "/api/v1/settlements/batches/" + batchId + "/settlements?page=0&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(adminHeaders),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private PageResponse<TransactionResponse> transactionsFor(UUID merchantId) {
        ResponseEntity<PageResponse<TransactionResponse>> response =
                restTemplate.exchange(
                        "/api/v1/transactions?merchantId=" + merchantId + "&page=0&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(adminHeaders),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private MerchantResponse createMerchant(String label, int mdrBps) {
        CreateMerchantRequest request =
                new CreateMerchantRequest(
                        label,
                        "merchant-" + UUID.randomUUID() + "@example.com",
                        "+1-555-0101",
                        mdrBps,
                        "BANK-" + label.toUpperCase());
        ResponseEntity<MerchantResponse> response =
                restTemplate.exchange(
                        "/api/v1/merchants",
                        HttpMethod.POST,
                        new HttpEntity<>(request, adminHeaders),
                        MerchantResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private TransactionResponse capture(
            UUID merchantId, String externalRef, long amountMinor, String currency, Instant at) {
        CreateTransactionRequest request =
                new CreateTransactionRequest(merchantId, externalRef, amountMinor, currency, at);
        ResponseEntity<TransactionResponse> response =
                restTemplate.exchange(
                        "/api/v1/transactions",
                        HttpMethod.POST,
                        new HttpEntity<>(request, adminHeaders),
                        TransactionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private HttpHeaders copyAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(adminHeaders);
        return headers;
    }

    private static SettlementResponse byMerchant(
            PageResponse<SettlementResponse> page, UUID merchantId) {
        return page.items().stream()
                .filter(settlement -> settlement.merchantId().equals(merchantId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No settlement for merchant " + merchantId));
    }
}
