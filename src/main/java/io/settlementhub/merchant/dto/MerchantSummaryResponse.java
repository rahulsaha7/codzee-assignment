package io.settlementhub.merchant.dto;

public record MerchantSummaryResponse(
        MerchantResponse merchant, long capturedTransactions, long settledTransactions) {}
