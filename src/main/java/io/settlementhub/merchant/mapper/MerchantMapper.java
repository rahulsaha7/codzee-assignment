package io.settlementhub.merchant.mapper;

import io.settlementhub.merchant.Merchant;
import io.settlementhub.merchant.dto.MerchantResponse;

public final class MerchantMapper {

    private MerchantMapper() {}

    public static MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getLegalName(),
                merchant.getBusinessEmail(),
                merchant.getPhone(),
                merchant.getStatus(),
                merchant.getMdrBps(),
                merchant.getBankAccountRef(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt(),
                merchant.getVersion());
    }
}
