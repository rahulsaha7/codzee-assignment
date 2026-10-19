package io.settlementhub.merchant;

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
import io.settlementhub.merchant.dto.CreateMerchantRequest;
import io.settlementhub.merchant.dto.MerchantResponse;
import io.settlementhub.merchant.dto.UpdateMerchantStatusRequest;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.CurrentUserProvider;
import io.settlementhub.security.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    private static final int MAX_PAGE_SIZE = 100;

    @Mock private MerchantRepository merchantRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private MerchantService merchantService;

    @BeforeEach
    void setUp() {
        AppProperties properties =
                new AppProperties(
                        new AppProperties.Jwt("test-secret", 3600),
                        new AppProperties.Admin("admin", "password"),
                        new AppProperties.Pagination(MAX_PAGE_SIZE),
                        new AppProperties.Settlement(4, 8, 100));
        merchantService = new MerchantService(merchantRepository, currentUserProvider, properties);
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
    }

    @Test
    void allowsMerchantToReadOwnRecord() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = merchant(merchantId, MerchantStatus.ACTIVE);
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, merchantId));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantResponse response = merchantService.get(merchantId);

        assertThat(response.id()).isEqualTo(merchantId);
    }

    @Test
    void forbidsMerchantFromReadingAnotherMerchant() {
        UUID merchantId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, UUID.randomUUID()));

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
        when(currentUserProvider.require()).thenReturn(new CurrentUser("ops-user", Role.OPS, null));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        assertThat(merchantService.get(merchantId).id()).isEqualTo(merchantId);
    }

    @Test
    void listsMerchantsFilteredByStatus() {
        Merchant merchant = merchant(UUID.randomUUID(), MerchantStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20);
        when(merchantRepository.findByStatus(
                        org.mockito.ArgumentMatchers.eq(MerchantStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(merchant), pageable, 1));

        PageResponse<MerchantResponse> response =
                merchantService.list(MerchantStatus.ACTIVE, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void rejectsPageSizeAboveCap() {
        assertThatThrownBy(() -> merchantService.list(null, 0, MAX_PAGE_SIZE + 1))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void updatesMerchantStatus() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = merchant(merchantId, MerchantStatus.PENDING);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantResponse response =
                merchantService.updateStatus(
                        merchantId, new UpdateMerchantStatusRequest(MerchantStatus.SUSPENDED));

        assertThat(response.status()).isEqualTo(MerchantStatus.SUSPENDED);
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
}
