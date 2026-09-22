package com.shreeya.vehicletitle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "title_transaction")
public class TitleTransaction {

    @Id
    private UUID id;

    @Version
    private long version;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 17)
    private String vin;

    @Column(nullable = false, length = 2)
    private String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private TransactionType transactionType;

    @Embedded
    private OwnerDetails owner;

    @Column(name = "purchase_price", precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TitleTransaction() {
    }

    public static TitleTransaction create(UUID id, String idempotencyKey, String requestHash,
                                          String vin, String jurisdiction, TransactionType transactionType,
                                          OwnerDetails owner, BigDecimal purchasePrice, Instant now) {
        TitleTransaction transaction = new TitleTransaction();
        transaction.id = id;
        transaction.idempotencyKey = idempotencyKey;
        transaction.requestHash = requestHash;
        transaction.vin = vin;
        transaction.jurisdiction = jurisdiction;
        transaction.transactionType = transactionType;
        transaction.owner = owner;
        transaction.purchasePrice = purchasePrice;
        transaction.status = TransactionStatus.RECEIVED;
        transaction.createdAt = now;
        transaction.updatedAt = now;
        return transaction;
    }

    public TransactionStatus transitionTo(TransactionStatus nextStatus, String externalReference,
                                           String rejectionReason, Instant now) {
        if (nextStatus == status) {
            return status;
        }
        if (!status.canTransitionTo(nextStatus)) {
            throw new InvalidTransitionException(status, nextStatus);
        }
        if (nextStatus == TransactionStatus.REJECTED
                && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("A rejection reason is required for REJECTED status");
        }
        TransactionStatus previous = status;
        status = nextStatus;
        String normalizedReference = normalize(externalReference);
        if (normalizedReference != null) {
            this.externalReference = normalizedReference;
        }
        this.rejectionReason = nextStatus == TransactionStatus.REJECTED
                ? normalize(rejectionReason) : null;
        updatedAt = now;
        return previous;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getVin() { return vin; }
    public String getJurisdiction() { return jurisdiction; }
    public TransactionType getTransactionType() { return transactionType; }
    public OwnerDetails getOwner() { return owner; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public TransactionStatus getStatus() { return status; }
    public String getExternalReference() { return externalReference; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
