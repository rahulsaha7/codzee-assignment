package io.settlementhub.merchant.dto;

import io.settlementhub.merchant.MerchantStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateMerchantStatusRequest(
        @NotNull(message = "must not be null") MerchantStatus status) {}
