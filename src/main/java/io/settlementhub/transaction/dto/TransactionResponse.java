package io.settlementhub.transaction.dto;

import io.settlementhub.transaction.TransactionStatus;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID merchantId,
        String externalRef,
        long amountMinor,
        String currency,
        TransactionStatus status,
        Instant capturedAt,
        UUID settlementId) {}
