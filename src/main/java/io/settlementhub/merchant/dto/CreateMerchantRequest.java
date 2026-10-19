package io.settlementhub.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMerchantRequest(
        @NotBlank(message = "must not be blank") @Size(max = 200) String legalName,
        @NotBlank(message = "must not be blank")
                @Email(message = "must be a valid email address")
                @Size(max = 200)
                String businessEmail,
        @Size(max = 20) String phone,
        @NotNull(message = "must not be null")
                @Min(value = 0, message = "must be between 0 and 2000")
                @Max(value = 2000, message = "must be between 0 and 2000")
                Integer mdrBps,
        @Size(max = 64) String bankAccountRef) {}
