package com.shreeya.vehicletitle.domain;

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

@Entity
@Table(name = "transaction_audit_event")
public class TransactionAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 32)
    private TransactionStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubmissionChannel channel;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(length = 2000)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TransactionAuditEvent() {
    }

    public TransactionAuditEvent(UUID transactionId, String eventType,
                                 TransactionStatus fromStatus, TransactionStatus toStatus,
                                 SubmissionChannel channel, String actor,
                                 String details, Instant occurredAt) {
        this.transactionId = transactionId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.channel = channel;
        this.actor = actor;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public String getEventType() { return eventType; }
    public TransactionStatus getFromStatus() { return fromStatus; }
    public TransactionStatus getToStatus() { return toStatus; }
    public SubmissionChannel getChannel() { return channel; }
    public String getActor() { return actor; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
