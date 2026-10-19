package io.settlementhub.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull(message = "must not be null") UUID merchantId,
        @NotBlank(message = "must not be blank") @Size(max = 64) String externalRef,
        @NotNull(message = "must not be null") @Positive(message = "must be greater than 0")
                Long amountMinor,
        @NotBlank(message = "must not be blank")
                @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO-4217 currency code")
                String currency,
        Instant capturedAt) {}
