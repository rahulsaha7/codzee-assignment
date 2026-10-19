package io.settlementhub.settlement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

public record CreateSettlementBatchRequest(
        @NotNull(message = "must not be null") @PastOrPresent(message = "must not be in the future")
                LocalDate businessDate) {}
