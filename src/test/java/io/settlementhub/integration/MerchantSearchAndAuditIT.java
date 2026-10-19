package io.settlementhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.settlementhub.common.web.PageResponse;
import io.settlementhub.merchant.MerchantStatus;
import io.settlementhub.merchant.dto.CreateMerchantRequest;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.merchant.dto.MerchantSummaryResponse;
import io.settlementhub.merchant.dto.UpdateMerchantStatusRequest;
import io.settlementhub.security.Role;
import io.settlementhub.security.user.UserAccount;
import io.settlementhub.security.user.UserRepository;
import io.settlementhub.security.user.dto.LoginRequest;
import io.settlementhub.security.user.dto.LoginResponse;
import io.settlementhub.transaction.TransactionStatus;
import io.settlementhub.transaction.dto.CreateTransactionRequest;
import io.settlementhub.transaction.dto.TransactionResponse;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class MerchantSearchAndAuditIT {

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
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void configurePatchSupport() {
        restTemplate
                .getRestTemplate()
                .setRequestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()));
    }

    @Test
    void searchesMerchantsByTextAndStatusAndWritesAuditRows() {
        HttpHeaders admin = bearer(login("admin", "test-admin-password"));
        String token = token();
        List<MerchantResponse> merchants = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            merchants.add(
                    createMerchant(
                            admin,
                            token + " Merchant " + index,
                            token.toLowerCase() + index + "@example.com",
                            "+1-555-01" + index));
        }
        String emailOnlyToken =
                "email" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MerchantResponse emailOnly =
                createMerchant(
                        admin,
                        "Email Only Co",
                        emailOnlyToken.toLowerCase() + "@example.com",
                        "+1-555-0199");

        MerchantResponse active = merchants.get(0);
        ResponseEntity<MerchantResponse> updated =
                restTemplate.exchange(
                        "/api/v1/merchants/" + active.id() + "/status",
                        HttpMethod.PATCH,
                        new HttpEntity<>(
                                new UpdateMerchantStatusRequest(MerchantStatus.ACTIVE), admin),
                        MerchantResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().status()).isEqualTo(MerchantStatus.ACTIVE);

        PageResponse<MerchantResponse> byName = search(admin, null, token.toUpperCase(), 0, 20);
        assertThat(byName.totalElements()).isEqualTo(5);
        assertThat(byName.items()).extracting(MerchantResponse::id).contains(active.id());

        PageResponse<MerchantResponse> byEmail =
                search(admin, null, emailOnlyToken.toUpperCase(), 0, 20);
        assertThat(byEmail.totalElements()).isEqualTo(1);
        assertThat(byEmail.items().get(0).id()).isEqualTo(emailOnly.id());

        PageResponse<MerchantResponse> activeOnly =
                search(admin, MerchantStatus.ACTIVE, token, 0, 20);
        assertThat(activeOnly.totalElements()).isEqualTo(1);
        assertThat(activeOnly.items().get(0).id()).isEqualTo(active.id());

        PageResponse<MerchantResponse> pendingOnly =
                search(admin, MerchantStatus.PENDING, token, 0, 20);
        assertThat(pendingOnly.totalElements()).isEqualTo(4);

        PageResponse<MerchantResponse> firstPage = search(admin, null, token, 0, 2);
        PageResponse<MerchantResponse> lastPage = search(admin, null, token, 2, 2);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(lastPage.items()).hasSize(1);
        assertThat(lastPage.page()).isEqualTo(2);

        assertThat(auditCount("MERCHANT_CREATED", active.id())).isEqualTo(1);
        assertThat(auditCount("MERCHANT_STATUS_CHANGED", active.id())).isEqualTo(1);
        assertThat(auditActor("MERCHANT_STATUS_CHANGED", active.id())).isEqualTo("admin");
    }

    @Test
    void merchantPortalScopesReadsAndReturnsOwnSummary() {
        HttpHeaders admin = bearer(login("admin", "test-admin-password"));
        String token = token();
        MerchantResponse own =
                createMerchant(
                        admin,
                        token + " Own Merchant",
                        token.toLowerCase() + "own@example.com",
                        "+1-555-0200");
        MerchantResponse other =
                createMerchant(
                        admin,
                        token + " Other Merchant",
                        token.toLowerCase() + "other@example.com",
                        "+1-555-0201");

        String username = "merchant-" + token.toLowerCase();
        userRepository.save(
                new UserAccount(
                        username,
                        passwordEncoder.encode("merchant-pass"),
                        Role.MERCHANT,
                        own.id()));
        HttpHeaders merchant = bearer(login(username, "merchant-pass"));

        ResponseEntity<MerchantResponse> ownResponse =
                restTemplate.exchange(
                        "/api/v1/merchants/" + own.id(),
                        HttpMethod.GET,
                        new HttpEntity<>(merchant),
                        MerchantResponse.class);
        assertThat(ownResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownResponse.getBody()).isNotNull();
        assertThat(ownResponse.getBody().id()).isEqualTo(own.id());

        ResponseEntity<String> otherResponse =
                restTemplate.exchange(
                        "/api/v1/merchants/" + other.id(),
                        HttpMethod.GET,
                        new HttpEntity<>(merchant),
                        String.class);
        assertThat(otherResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> otherTransactions =
                restTemplate.exchange(
                        "/api/v1/transactions?merchantId=" + other.id() + "&page=0&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(merchant),
                        String.class);
        assertThat(otherTransactions.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        TransactionResponse captured =
                captureTransaction(admin, own.id(), "order-" + UUID.randomUUID(), 12500L);
        TransactionResponse settled =
                captureTransaction(admin, own.id(), "order-" + UUID.randomUUID(), 8000L);
        jdbcTemplate.update(
                "update transactions set status = 'SETTLED' where id = ?::uuid",
                settled.id().toString());

        ResponseEntity<PageResponse<TransactionResponse>> ownTransactions =
                restTemplate.exchange(
                        "/api/v1/transactions?merchantId=" + own.id() + "&page=0&size=20",
                        HttpMethod.GET,
                        new HttpEntity<>(merchant),
                        new ParameterizedTypeReference<>() {});
        assertThat(ownTransactions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownTransactions.getBody()).isNotNull();
        assertThat(ownTransactions.getBody().items())
                .extracting(TransactionResponse::id)
                .contains(captured.id(), settled.id());

        ResponseEntity<MerchantSummaryResponse> summary =
                restTemplate.exchange(
                        "/api/v1/merchants/me/summary",
                        HttpMethod.GET,
                        new HttpEntity<>(merchant),
                        MerchantSummaryResponse.class);
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody()).isNotNull();
        assertThat(summary.getBody().merchant().id()).isEqualTo(own.id());
        assertThat(summary.getBody().capturedTransactions()).isEqualTo(1);
        assertThat(summary.getBody().settledTransactions()).isEqualTo(1);

        ResponseEntity<String> adminSummary =
                restTemplate.exchange(
                        "/api/v1/merchants/me/summary",
                        HttpMethod.GET,
                        new HttpEntity<>(admin),
                        String.class);
        assertThat(adminSummary.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> anonymousSummary =
                restTemplate.getForEntity("/api/v1/merchants/me/summary", String.class);
        assertThat(anonymousSummary.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String login(String username, String password) {
        ResponseEntity<LoginResponse> response =
                restTemplate.postForEntity(
                        "/api/v1/auth/login",
                        new LoginRequest(username, password),
                        LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().accessToken();
    }

    private MerchantResponse createMerchant(
            HttpHeaders headers, String legalName, String businessEmail, String phone) {
        ResponseEntity<MerchantResponse> response =
                restTemplate.exchange(
                        "/api/v1/merchants",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new CreateMerchantRequest(
                                        legalName,
                                        businessEmail,
                                        phone,
                                        150,
                                        "BANK-" + UUID.randomUUID()),
                                headers),
                        MerchantResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private PageResponse<MerchantResponse> search(
            HttpHeaders headers, MerchantStatus status, String q, int page, int size) {
        StringBuilder url =
                new StringBuilder("/api/v1/merchants?page=")
                        .append(page)
                        .append("&size=")
                        .append(size);
        if (status != null) {
            url.append("&status=").append(status);
        }
        if (q != null) {
            url.append("&q=").append(q);
        }
        ResponseEntity<PageResponse<MerchantResponse>> response =
                restTemplate.exchange(
                        url.toString(),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private TransactionResponse captureTransaction(
            HttpHeaders headers, UUID merchantId, String externalRef, long amountMinor) {
        ResponseEntity<TransactionResponse> response =
                restTemplate.exchange(
                        "/api/v1/transactions",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new CreateTransactionRequest(
                                        merchantId, externalRef, amountMinor, "USD", Instant.now()),
                                headers),
                        TransactionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(TransactionStatus.CAPTURED);
        return response.getBody();
    }

    private int auditCount(String action, UUID entityId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "select count(*) from audit_log where action = ? and entity_id = ?",
                        Integer.class,
                        action,
                        entityId.toString());
        return count == null ? 0 : count;
    }

    private String auditActor(String action, UUID entityId) {
        return jdbcTemplate.queryForObject(
                "select actor from audit_log where action = ? and entity_id = ?",
                String.class,
                action,
                entityId.toString());
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static String token() {
        return "scope" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
