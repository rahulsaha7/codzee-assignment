package io.settlementhub.transaction;

import io.settlementhub.transaction.dto.TransactionResponse;

public record TransactionCreationResult(TransactionResponse transaction, boolean created) {}
