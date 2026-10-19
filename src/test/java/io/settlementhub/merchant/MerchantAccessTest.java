package io.settlementhub.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.CurrentUserProvider;
import io.settlementhub.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantAccessTest {

    @Mock private CurrentUserProvider currentUserProvider;

    private MerchantAccess merchantAccess;

    @BeforeEach
    void setUp() {
        merchantAccess = new MerchantAccess(currentUserProvider);
    }

    @Test
    void adminAndOpsCanAccessAnyMerchant() {
        UUID merchantId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("admin", Role.ADMIN, null))
                .thenReturn(new CurrentUser("ops", Role.OPS, null));

        assertThatCode(() -> merchantAccess.requireAccess(merchantId)).doesNotThrowAnyException();
        assertThatCode(() -> merchantAccess.requireAccess(merchantId)).doesNotThrowAnyException();
    }

    @Test
    void merchantCanAccessOwnMerchant() {
        UUID merchantId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, merchantId));

        assertThatCode(() -> merchantAccess.requireAccess(merchantId)).doesNotThrowAnyException();
    }

    @Test
    void merchantCannotAccessAnotherMerchant() {
        UUID ownMerchantId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, ownMerchantId));

        assertThatThrownBy(() -> merchantAccess.requireAccess(UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void returnsLinkedMerchantIdForMerchantUser() {
        UUID merchantId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, merchantId));

        assertThat(merchantAccess.requireLinkedMerchantId()).isEqualTo(merchantId);
    }

    @Test
    void rejectsSummaryForAdminWithoutMerchantLink() {
        when(currentUserProvider.require()).thenReturn(new CurrentUser("admin", Role.ADMIN, null));

        assertThatThrownBy(() -> merchantAccess.requireLinkedMerchantId())
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void rejectsSummaryForUnlinkedMerchantUser() {
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, null));

        assertThatThrownBy(() -> merchantAccess.requireLinkedMerchantId())
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void adminTransactionFilterPassesThrough() {
        UUID requestedMerchantId = UUID.randomUUID();
        when(currentUserProvider.require()).thenReturn(new CurrentUser("admin", Role.ADMIN, null));

        assertThat(merchantAccess.resolveTransactionFilter(requestedMerchantId))
                .isEqualTo(requestedMerchantId);
    }

    @Test
    void merchantCanFilterOwnTransactions() {
        UUID merchantId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, merchantId));

        assertThat(merchantAccess.resolveTransactionFilter(merchantId)).isEqualTo(merchantId);
    }

    @Test
    void merchantCannotFilterAnotherMerchantsTransactions() {
        UUID ownMerchantId = UUID.randomUUID();
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, ownMerchantId));

        assertThatThrownBy(() -> merchantAccess.resolveTransactionFilter(UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void unlinkedMerchantCannotResolveTransactions() {
        when(currentUserProvider.require())
                .thenReturn(new CurrentUser("merchant-user", Role.MERCHANT, null));

        assertThatThrownBy(() -> merchantAccess.resolveTransactionFilter(null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
    }
}
