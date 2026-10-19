package io.settlementhub.settlement.mapper;

import io.settlementhub.settlement.Settlement;
import io.settlementhub.settlement.SettlementBatch;
import io.settlementhub.settlement.dto.SettlementBatchResponse;
import io.settlementhub.settlement.dto.SettlementResponse;

public final class SettlementMapper {

    private SettlementMapper() {}

    public static SettlementBatchResponse toResponse(SettlementBatch batch) {
        return new SettlementBatchResponse(
                batch.getId(),
                batch.getBusinessDate(),
                batch.getStatus(),
                batch.getTotalMerchants(),
                batch.getProcessedMerchants(),
                batch.getFailedMerchants(),
                batch.getStartedAt(),
                batch.getCompletedAt(),
                batch.getCreatedAt());
    }

    public static SettlementResponse toResponse(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getBatchId(),
                settlement.getMerchantId(),
                settlement.getGrossMinor(),
                settlement.getMdrMinor(),
                settlement.getNetMinor(),
                settlement.getTxnCount(),
                settlement.getCurrency(),
                settlement.getStatus(),
                settlement.getFailureReason(),
                settlement.getCreatedAt());
    }
}
