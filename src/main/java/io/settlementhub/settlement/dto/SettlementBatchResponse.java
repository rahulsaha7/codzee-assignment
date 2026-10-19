package io.settlementhub.settlement.dto;

import io.settlementhub.settlement.SettlementBatchStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementBatchResponse(
        UUID id,
        LocalDate businessDate,
        SettlementBatchStatus status,
        int totalMerchants,
        int processedMerchants,
        int failedMerchants,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {}
