package io.settlementhub.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "gross_minor")
    private Long grossMinor;

    @Column(name = "mdr_minor")
    private Long mdrMinor;

    @Column(name = "net_minor")
    private Long netMinor;

    @Column(name = "txn_count")
    private Integer txnCount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettlementStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Settlement() {}

    private Settlement(
            UUID batchId,
            UUID merchantId,
            Long grossMinor,
            Long mdrMinor,
            Long netMinor,
            Integer txnCount,
            String currency,
            SettlementStatus status,
            String failureReason) {
        this.batchId = batchId;
        this.merchantId = merchantId;
        this.grossMinor = grossMinor;
        this.mdrMinor = mdrMinor;
        this.netMinor = netMinor;
        this.txnCount = txnCount;
        this.currency = currency;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    public static Settlement settled(
            UUID batchId,
            UUID merchantId,
            long grossMinor,
            long mdrMinor,
            long netMinor,
            int txnCount,
            String currency) {
        return new Settlement(
                batchId,
                merchantId,
                grossMinor,
                mdrMinor,
                netMinor,
                txnCount,
                currency,
                SettlementStatus.CREATED,
                null);
    }

    public static Settlement failed(
            UUID batchId, UUID merchantId, int txnCount, String failureReason) {
        return new Settlement(
                batchId,
                merchantId,
                null,
                null,
                null,
                txnCount,
                null,
                SettlementStatus.FAILED,
                failureReason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Long getGrossMinor() {
        return grossMinor;
    }

    public Long getMdrMinor() {
        return mdrMinor;
    }

    public Long getNetMinor() {
        return netMinor;
    }

    public Integer getTxnCount() {
        return txnCount;
    }

    public String getCurrency() {
        return currency;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
