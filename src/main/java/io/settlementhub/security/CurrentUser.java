package io.settlementhub.security;

import java.util.UUID;

public record CurrentUser(String username, Role role, UUID merchantId) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isOps() {
        return role == Role.OPS;
    }

    public boolean isMerchant() {
        return role == Role.MERCHANT;
    }

    public boolean isAdminOrOps() {
        return isAdmin() || isOps();
    }

    public boolean canAccessMerchant(UUID targetMerchantId) {
        return isAdminOrOps() || (merchantId != null && merchantId.equals(targetMerchantId));
    }
}
