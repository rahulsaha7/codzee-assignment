package io.settlementhub.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.settlementhub.common.config.AppProperties;
import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.merchant.MerchantRepository;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.CurrentUserProvider;
import io.settlementhub.security.Role;
import io.settlementhub.transaction.dto.CreateTransactionRequest;
import io.settlementhub.transaction.dto.TransactionResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        AppProperties properties =
                new AppProperties(
                        new AppProperties.Jwt("test-secret", 3600),
                        new AppProperties.Admin("admin", "password"),
                        new AppProperties.Pagination(100),
                        new AppProperties.Settlement(4, 8, 100));
        transactionService =
                new TransactionService(
                        transactionRepository, merchantRepository, currentUserProvider, properties);
    }

    @Test
    void createsCapturedTransaction() {
        UUID merchantId = UUID.randomUUID();
        CreateTransactionRequest request =
                new CreateTransactionRequest(merchantId, "order-1", 12500L, "USD", null);
        when(merchantRepository.existsById(merchantId)).thenReturn(true);
        when(transactionRepository.findByMerchantIdAndExternalRef(merchantId, "order-1"))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction transaction = invocation.getArgument(0);
                            ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
                            return transaction;
                        });

        TransactionCreationResult result = transactionService.capture(request);

        assertThat(result.created()).isTrue();
        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.CAPTURED);
        assertThat(result.transaction().currency()).isEqualTo("USD");
        assertThat(result.transaction().capturedAt()).isNotNull();
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void returnsExistingTransactionForRepeatedExternalReference() {
        UUID merchantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Transaction existing =
                new Transaction(
                        merchantId,
                        "order-1",
                        12500L,
                        "USD",
                        TransactionStatus.CAPTURED,
                        Instant.parse("2026-01-10T10:00:00Z"),
                        null);
        ReflectionTestUtils.setField(existing, "id", transactionId);
        CreateTransactionRequest request =
                new CreateTransactionRequest(merchantId, "order-1", 99999L, "EUR", Instant.now());
        when(merchantRepository.existsById(merchantId)).thenReturn(true);
        when(transactionRepository.findByMerchantIdAndExternalRef(merchantId, "order-1"))
                .thenReturn(Optional.of(existing));

        TransactionCreationResult result = transactionService.capture(request);

        assertThat(result.created()).isFalse();
        assertThat(result.transaction().id()).isEqualTo(transactionId);
        assertThat(result.transaction().amountMinor()).isEqualTo(12500L);
        assertThat(result.transaction().currency()).isEqualTo("USD");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsCaptureForUnknownMerchant() {
        UUID merchantId = UUID.randomUUID();
        when(merchantRepository.existsById(merchantId)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                transactionService.capture(
                                        new CreateTransactionRequest(
                                                merchantId, "order-1", 100L, "USD", null)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void scopesMerchantListingToOwnMerchantWhenFilterIsOmitted() {
        UUID merchantId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, merchantId));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<TransactionResponse> response =
                transactionService.list(null, null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void forbidsMerchantFromListingAnotherMerchantsTransactions() {
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, UUID.randomUUID()));

        assertThatThrownBy(() -> transactionService.list(UUID.randomUUID(), null, null, 0, 20))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(transactionRepository, never())
                .findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void allowsAdminToFilterByMerchant() {
        UUID merchantId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(new CurrentUser("admin", Role.ADMIN, null));
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        transactionService.list(merchantId, null, null, 0, 20);

        verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsInvalidDateRange() {
        Instant from = Instant.parse("2026-02-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> transactionService.list(null, from, to, 0, 20))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }
}
