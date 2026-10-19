package io.settlementhub.transaction;

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
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "settlement_id")
    private UUID settlementId;

    protected Transaction() {}

    public Transaction(
            UUID merchantId,
            String externalRef,
            long amountMinor,
            String currency,
            TransactionStatus status,
            Instant capturedAt,
            UUID settlementId) {
        this.merchantId = merchantId;
        this.externalRef = externalRef;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.capturedAt = capturedAt;
        this.settlementId = settlementId;
    }

    public void markSettled(UUID settlementId) {
        this.status = TransactionStatus.SETTLED;
        this.settlementId = settlementId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public UUID getSettlementId() {
        return settlementId;
    }
}
