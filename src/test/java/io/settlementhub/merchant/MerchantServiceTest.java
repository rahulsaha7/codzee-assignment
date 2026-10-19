package io.settlementhub.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.settlementhub.common.audit.AuditService;
import io.settlementhub.common.config.AppProperties;
import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.common.web.PageResponse;
import io.settlementhub.merchant.dto.CreateMerchantRequest;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.merchant.dto.MerchantSummaryResponse;
import io.settlementhub.merchant.dto.UpdateMerchantStatusRequest;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.Role;
import io.settlementhub.transaction.TransactionRepository;
import io.settlementhub.transaction.TransactionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    private static final int MAX_PAGE_SIZE = 100;

    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantSearchRepository merchantSearchRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private MerchantAccess merchantAccess;
    @Mock private AuditService auditService;

    private MerchantService merchantService;

    @BeforeEach
    void setUp() {
        AppProperties properties =
                new AppProperties(
                        new AppProperties.Jwt("test-secret", 3600),
                        new AppProperties.Admin("admin", "password"),
                        new AppProperties.Pagination(MAX_PAGE_SIZE),
                        new AppProperties.Settlement(4, 8, 100));
        merchantService =
                new MerchantService(
                        merchantRepository,
                        merchantSearchRepository,
                        transactionRepository,
                        merchantAccess,
                        auditService,
                        properties);
    }

    @Test
    void createsMerchantInPendingStatus() {
        CreateMerchantRequest request =
                new CreateMerchantRequest("Acme", "acme@example.com", "+1-555-0100", 150, "BANK-1");
        when(merchantRepository.existsByBusinessEmail(request.businessEmail())).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class)))
                .thenAnswer(
                        invocation -> {
                            Merchant merchant = invocation.getArgument(0);
                            ReflectionTestUtils.setField(merchant, "id", UUID.randomUUID());
                            return merchant;
                        });

        MerchantResponse response = merchantService.create(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.legalName()).isEqualTo("Acme");
        assertThat(response.status()).isEqualTo(MerchantStatus.PENDING);
        assertThat(response.mdrBps()).isEqualTo(150);
        verify(auditService).merchantCreated(any(Merchant.class));
    }

    @Test
    void rejectsDuplicateBusinessEmail() {
        CreateMerchantRequest request =
                new CreateMerchantRequest("Acme", "acme@example.com", null, 150, null);
        when(merchantRepository.existsByBusinessEmail(request.businessEmail())).thenReturn(true);

        assertThatThrownBy(() -> merchantService.create(request))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(merchantRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void allowsMerchantToReadOwnRecord() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = merchant(merchantId, MerchantStatus.ACTIVE);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantResponse response = merchantService.get(merchantId);

        assertThat(response.id()).isEqualTo(merchantId);
        verify(merchantAccess).requireAccess(merchantId);
    }

    @Test
    void forbidsMerchantFromReadingAnotherMerchant() {
        UUID merchantId = UUID.randomUUID();
        doThrow(new ApiException(ErrorCode.FORBIDDEN, "You do not have access to this merchant."))
                .when(merchantAccess)
                .requireAccess(merchantId);

        assertThatThrownBy(() -> merchantService.get(merchantId))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(merchantRepository, never()).findById(any());
    }

    @Test
    void allowsOpsToReadAnyMerchant() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = merchant(merchantId, MerchantStatus.ACTIVE);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        assertThat(merchantService.get(merchantId).id()).isEqualTo(merchantId);
        verify(merchantAccess).requireAccess(merchantId);
    }

    @Test
    void searchesMerchantsWithTextAndStatusFilters() {
        MerchantResponse merchant = response(UUID.randomUUID(), "Acme", "acme@example.com");
        when(merchantSearchRepository.search(MerchantStatus.ACTIVE, "acme", 0, 20))
                .thenReturn(new PageResponse<>(List.of(merchant), 0, 20, 1, 1));

        PageResponse<MerchantResponse> response =
                merchantService.search(MerchantStatus.ACTIVE, "acme", 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void rejectsPageSizeAboveCap() {
        assertThatThrownBy(() -> merchantService.search(null, null, 0, MAX_PAGE_SIZE + 1))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(merchantSearchRepository);
    }

    @Test
    void updatesMerchantStatusAndRecordsAudit() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = merchant(merchantId, MerchantStatus.PENDING);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantResponse response =
                merchantService.updateStatus(
                        merchantId, new UpdateMerchantStatusRequest(MerchantStatus.SUSPENDED));

        assertThat(response.status()).isEqualTo(MerchantStatus.SUSPENDED);
        verify(auditService).merchantStatusChanged(merchant, MerchantStatus.PENDING);
    }

    @Test
    void returnsNotFoundForUnknownMerchant() {
        UUID merchantId = UUID.randomUUID();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                merchantService.updateStatus(
                                        merchantId,
                                        new UpdateMerchantStatusRequest(MerchantStatus.ACTIVE)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void returnsSummaryCountsForLinkedMerchant() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = merchant(merchantId, MerchantStatus.ACTIVE);
        when(merchantAccess.requireLinkedMerchantId()).thenReturn(merchantId);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(transactionRepository.countByMerchantIdAndStatus(
                        merchantId, TransactionStatus.CAPTURED))
                .thenReturn(4L);
        when(transactionRepository.countByMerchantIdAndStatus(
                        merchantId, TransactionStatus.SETTLED))
                .thenReturn(3L);

        MerchantSummaryResponse summary = merchantService.mySummary();

        assertThat(summary.merchant().id()).isEqualTo(merchantId);
        assertThat(summary.capturedTransactions()).isEqualTo(4);
        assertThat(summary.settledTransactions()).isEqualTo(3);
    }

    @Test
    void rejectsSummaryForUnlinkedAccount() {
        when(merchantAccess.requireLinkedMerchantId())
                .thenThrow(
                        new ApiException(
                                ErrorCode.FORBIDDEN, "This account is not linked to a merchant."));

        assertThatThrownBy(() -> merchantService.mySummary())
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void merchantCannotAccessAnotherMerchantsTransactionsThroughOwnershipHelper() {
        UUID ownMerchantId = UUID.randomUUID();
        CurrentUser merchantUser = new CurrentUser("merchant-user", Role.MERCHANT, ownMerchantId);

        assertThat(merchantUser.canAccessMerchant(ownMerchantId)).isTrue();
        assertThat(merchantUser.canAccessMerchant(UUID.randomUUID())).isFalse();
        assertThat(merchantUser.canAccessMerchant(null)).isFalse();
    }

    private static Merchant merchant(UUID id, MerchantStatus status) {
        Merchant merchant = new Merchant("Acme", "acme@example.com", null, 150, null, status);
        ReflectionTestUtils.setField(merchant, "id", id);
        return merchant;
    }

    private static MerchantResponse response(UUID id, String legalName, String email) {
        return new MerchantResponse(
                id,
                legalName,
                email,
                null,
                MerchantStatus.ACTIVE,
                150,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                0);
    }
}
