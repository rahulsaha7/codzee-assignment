package io.settlementhub.merchant.dto;

import io.settlementhub.merchant.MerchantStatus;
import java.time.Instant;
import java.util.UUID;

public record MerchantResponse(
        UUID id,
        String legalName,
        String businessEmail,
        String phone,
        MerchantStatus status,
        int mdrBps,
        String bankAccountRef,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
