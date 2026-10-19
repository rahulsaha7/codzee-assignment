package io.settlementhub.settlement.dto;

import io.settlementhub.settlement.SettlementStatus;
import java.time.Instant;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        UUID batchId,
        UUID merchantId,
        Long grossMinor,
        Long mdrMinor,
        Long netMinor,
        Integer txnCount,
        String currency,
        SettlementStatus status,
        String failureReason,
        Instant createdAt) {}
