package io.settlementhub.merchant;

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
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "business_email", nullable = false, unique = true, length = 200)
    private String businessEmail;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MerchantStatus status;

    @Column(name = "mdr_bps", nullable = false)
    private int mdrBps;

    @Column(name = "bank_account_ref", length = 64)
    private String bankAccountRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Merchant() {}

    public Merchant(
            String legalName,
            String businessEmail,
            String phone,
            int mdrBps,
            String bankAccountRef,
            MerchantStatus status) {
        this.legalName = legalName;
        this.businessEmail = businessEmail;
        this.phone = phone;
        this.mdrBps = mdrBps;
        this.bankAccountRef = bankAccountRef;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeStatus(MerchantStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getBusinessEmail() {
        return businessEmail;
    }

    public String getPhone() {
        return phone;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public int getMdrBps() {
        return mdrBps;
    }

    public String getBankAccountRef() {
        return bankAccountRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
