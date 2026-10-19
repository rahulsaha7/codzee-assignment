package io.settlementhub.merchant;

import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.security.CurrentUser;
import io.settlementhub.security.CurrentUserProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MerchantAccess {

    private final CurrentUserProvider currentUserProvider;

    public MerchantAccess(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    public void requireAccess(UUID merchantId) {
        CurrentUser currentUser = currentUserProvider.require();
        if (!currentUser.canAccessMerchant(merchantId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have access to this merchant.");
        }
    }

    public UUID requireLinkedMerchantId() {
        CurrentUser currentUser = currentUserProvider.require();
        if (!currentUser.isMerchant() || currentUser.merchantId() == null) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN, "This account is not linked to a merchant.");
        }
        return currentUser.merchantId();
    }

    public UUID resolveTransactionFilter(UUID requestedMerchantId) {
        CurrentUser currentUser = currentUserProvider.require();
        if (!currentUser.isMerchant()) {
            return requestedMerchantId;
        }
        if (currentUser.merchantId() == null) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN, "This account is not linked to a merchant.");
        }
        if (requestedMerchantId != null && !requestedMerchantId.equals(currentUser.merchantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have access to this merchant.");
        }
        return requestedMerchantId;
    }
}
