package io.settlementhub.security.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "must not be blank") @Size(max = 64) String username,
        @NotBlank(message = "must not be blank") @Size(max = 200) String password) {}
