package io.settlementhub.settlement;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.settlementhub.common.error.ApiException;
import io.settlementhub.common.error.ErrorCode;
import io.settlementhub.common.money.MdrCalculator;
import io.settlementhub.merchant.Merchant;
import io.settlementhub.merchant.MerchantRepository;
import io.settlementhub.transaction.Transaction;
import io.settlementhub.transaction.TransactionRepository;
import io.settlementhub.transaction.TransactionStatus;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SettlementWorker {

    private static final Logger log = LoggerFactory.getLogger(SettlementWorker.class);

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementRepository settlementRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final MdrCalculator mdrCalculator;
    private final Counter processedCounter;
    private final Counter failedCounter;

    public SettlementWorker(
            SettlementBatchRepository settlementBatchRepository,
            SettlementRepository settlementRepository,
            TransactionRepository transactionRepository,
            MerchantRepository merchantRepository,
            MdrCalculator mdrCalculator,
            MeterRegistry meterRegistry) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementRepository = settlementRepository;
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.mdrCalculator = mdrCalculator;
        this.processedCounter = meterRegistry.counter("settlements.processed");
        this.failedCounter = meterRegistry.counter("settlements.failed");
    }

    public void run(UUID batchId) {
        try {
            SettlementBatch batch = requireBatch(batchId);
            Instant from = batch.getBusinessDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to =
                    batch.getBusinessDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            List<UUID> merchantIds =
                    transactionRepository.findMerchantIdsForSettlement(
                            TransactionStatus.CAPTURED, from, to);
            batch.markRunning(merchantIds.size());
            settlementBatchRepository.save(batch);

            for (UUID merchantId : merchantIds) {
                try {
                    Outcome outcome = settleMerchant(batchId, merchantId, from, to);
                    recordMerchant(batchId, outcome == Outcome.FAILED);
                    if (outcome == Outcome.FAILED) {
                        failedCounter.increment();
                    } else {
                        processedCounter.increment();
                    }
                } catch (Exception exception) {
                    log.debug(
                            "Merchant {} in settlement batch {} could not be settled",
                            merchantId,
                            batchId,
                            exception);
                    recordMerchant(batchId, false);
                    processedCounter.increment();
                }
            }

            finish(batchId);
        } catch (Exception exception) {
            log.error("Settlement batch {} failed", batchId, exception);
            markFailed(batchId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome settleMerchant(UUID batchId, UUID merchantId, Instant from, Instant to) {
        Merchant merchant =
                merchantRepository
                        .findById(merchantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.NOT_FOUND,
                                                "Merchant not found: " + merchantId));
        List<Transaction> transactions =
                transactionRepository.findForSettlement(
                        merchantId, TransactionStatus.CAPTURED, from, to);
        if (transactions.isEmpty()) {
            return Outcome.SKIPPED;
        }

        List<String> currencies =
                transactions.stream().map(Transaction::getCurrency).distinct().toList();
        if (currencies.size() > 1) {
            String reason = "Transactions span multiple currencies";
            settlementRepository.save(
                    Settlement.failed(batchId, merchantId, transactions.size(), reason));
            return Outcome.FAILED;
        }

        String currency = currencies.getFirst();
        long grossMinor = transactions.stream().mapToLong(Transaction::getAmountMinor).sum();
        long mdrMinor = mdrCalculator.calculate(grossMinor, merchant.getMdrBps());
        long netMinor = grossMinor - mdrMinor;
        Settlement settlement =
                settlementRepository.save(
                        Settlement.settled(
                                batchId,
                                merchantId,
                                grossMinor,
                                mdrMinor,
                                netMinor,
                                transactions.size(),
                                currency));
        for (Transaction transaction : transactions) {
            transaction.markSettled(settlement.getId());
        }
        transactionRepository.saveAll(transactions);
        return Outcome.SETTLED;
    }

    private void recordMerchant(UUID batchId, boolean failed) {
        SettlementBatch batch = requireBatch(batchId);
        batch.recordProcessed();
        if (failed) {
            batch.recordFailed();
        }
        settlementBatchRepository.save(batch);
    }

    private void finish(UUID batchId) {
        SettlementBatch batch = requireBatch(batchId);
        SettlementBatchStatus status;
        if (batch.getFailedMerchants() == 0) {
            status = SettlementBatchStatus.COMPLETED;
        } else if (batch.getFailedMerchants() >= batch.getTotalMerchants()) {
            status = SettlementBatchStatus.FAILED;
        } else {
            status = SettlementBatchStatus.PARTIAL_FAILED;
        }
        batch.complete(status);
        settlementBatchRepository.save(batch);
    }

    private void markFailed(UUID batchId) {
        settlementBatchRepository
                .findById(batchId)
                .ifPresent(
                        batch -> {
                            batch.complete(SettlementBatchStatus.FAILED);
                            settlementBatchRepository.save(batch);
                        });
    }

    private SettlementBatch requireBatch(UUID batchId) {
        return settlementBatchRepository
                .findById(batchId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND,
                                        "Settlement batch not found: " + batchId));
    }

    public enum Outcome {
        SETTLED,
        SKIPPED,
        FAILED
    }
}
