package io.settlementhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.settlementhub.common.web.PageResponse;
import io.settlementhub.merchant.MerchantStatus;
import io.settlementhub.merchant.dto.CreateMerchantRequest;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.security.user.dto.LoginRequest;
import io.settlementhub.security.user.dto.LoginResponse;
import io.settlementhub.transaction.TransactionStatus;
import io.settlementhub.transaction.dto.CreateTransactionRequest;
import io.settlementhub.transaction.dto.TransactionResponse;
import java.time.Instant;
import java.util.UUID;
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
class SettlementHubFlowIT {

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

    @Test
    void adminCanLoginCreateMerchantAndCaptureIdempotentTransaction() {
        LoginResponse login =
                restTemplate.postForObject(
                        "/api/v1/auth/login",
                        new LoginRequest("admin", "test-admin-password"),
                        LoginResponse.class);
        assertThat(login).isNotNull();
        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.expiresInSeconds()).isPositive();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        CreateMerchantRequest merchantRequest =
                new CreateMerchantRequest(
                        "Integration Merchant",
                        "integration-" + System.nanoTime() + "@example.com",
                        "+1-555-0190",
                        175,
                        "BANK-INT-1");
        ResponseEntity<MerchantResponse> merchantResponse =
                restTemplate.exchange(
                        "/api/v1/merchants",
                        HttpMethod.POST,
                        new HttpEntity<>(merchantRequest, headers),
                        MerchantResponse.class);

        assertThat(merchantResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        MerchantResponse merchant = merchantResponse.getBody();
        assertThat(merchant).isNotNull();
        assertThat(merchant.id()).isNotNull();
        assertThat(merchant.status()).isEqualTo(MerchantStatus.PENDING);

        CreateTransactionRequest transactionRequest =
                new CreateTransactionRequest(
                        merchant.id(),
                        "order-integration-1",
                        25000L,
                        "USD",
                        Instant.parse("2026-01-15T09:30:00Z"));
        ResponseEntity<TransactionResponse> transactionResponse =
                restTemplate.exchange(
                        "/api/v1/transactions",
                        HttpMethod.POST,
                        new HttpEntity<>(transactionRequest, headers),
                        TransactionResponse.class);

        assertThat(transactionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransactionResponse transaction = transactionResponse.getBody();
        assertThat(transaction).isNotNull();
        assertThat(transaction.id()).isNotNull();
        assertThat(transaction.merchantId()).isEqualTo(merchant.id());
        assertThat(transaction.status()).isEqualTo(TransactionStatus.CAPTURED);
        assertThat(transaction.amountMinor()).isEqualTo(25000L);
        assertThat(transaction.currency()).isEqualTo("USD");

        ResponseEntity<TransactionResponse> repeatedResponse =
                restTemplate.exchange(
                        "/api/v1/transactions",
                        HttpMethod.POST,
                        new HttpEntity<>(transactionRequest, headers),
                        TransactionResponse.class);

        assertThat(repeatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeatedResponse.getBody()).isNotNull();
        assertThat(repeatedResponse.getBody().id()).isEqualTo(transaction.id());

        ResponseEntity<PageResponse<TransactionResponse>> filtered =
                restTemplate.exchange(
                        "/api/v1/transactions?merchantId="
                                + merchant.id()
                                + "&from=2026-01-01T00:00:00Z&to=2026-01-31T23:59:59Z"
                                + "&page=0&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filtered.getBody()).isNotNull();
        assertThat(filtered.getBody().items()).hasSize(1);
        assertThat(filtered.getBody().items().get(0).id()).isEqualTo(transaction.id());

        ResponseEntity<PageResponse<TransactionResponse>> otherMerchant =
                restTemplate.exchange(
                        "/api/v1/transactions?merchantId=" + UUID.randomUUID() + "&page=0&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(otherMerchant.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otherMerchant.getBody()).isNotNull();
        assertThat(otherMerchant.getBody().items()).isEmpty();

        ResponseEntity<PageResponse<TransactionResponse>> outsideRange =
                restTemplate.exchange(
                        "/api/v1/transactions?merchantId="
                                + merchant.id()
                                + "&from=2026-02-01T00:00:00Z&to=2026-02-28T23:59:59Z"
                                + "&page=0&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(outsideRange.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(outsideRange.getBody()).isNotNull();
        assertThat(outsideRange.getBody().items()).isEmpty();
    }
}
