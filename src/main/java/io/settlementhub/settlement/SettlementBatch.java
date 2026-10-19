package io.settlementhub.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "settlement_batches")
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementBatchStatus status;

    @Column(name = "total_merchants", nullable = false)
    private int totalMerchants;

    @Column(name = "processed_merchants", nullable = false)
    private int processedMerchants;

    @Column(name = "failed_merchants", nullable = false)
    private int failedMerchants;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 80)
    private String idempotencyKey;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SettlementBatch() {}

    public SettlementBatch(LocalDate businessDate, String idempotencyKey) {
        this.businessDate = businessDate;
        this.idempotencyKey = idempotencyKey;
        this.status = SettlementBatchStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public void markRunning(int merchantCount) {
        this.status = SettlementBatchStatus.RUNNING;
        this.totalMerchants = merchantCount;
        this.startedAt = Instant.now();
    }

    public void recordProcessed() {
        this.processedMerchants++;
    }

    public void recordFailed() {
        this.failedMerchants++;
    }

    public void complete(SettlementBatchStatus finalStatus) {
        this.status = finalStatus;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public SettlementBatchStatus getStatus() {
        return status;
    }

    public int getTotalMerchants() {
        return totalMerchants;
    }

    public int getProcessedMerchants() {
        return processedMerchants;
    }

    public int getFailedMerchants() {
        return failedMerchants;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
