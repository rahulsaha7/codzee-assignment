package io.settlementhub.transaction.mapper;

import io.settlementhub.transaction.Transaction;
import io.settlementhub.transaction.dto.TransactionResponse;

public final class TransactionMapper {

    private TransactionMapper() {}

    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getMerchantId(),
                transaction.getExternalRef(),
                transaction.getAmountMinor(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getCapturedAt(),
                transaction.getSettlementId());
    }
}
