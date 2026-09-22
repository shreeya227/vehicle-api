package com.shreeya.vehicletitle.api.rest;

import com.shreeya.vehicletitle.domain.SubmissionChannel;
import com.shreeya.vehicletitle.domain.TransactionAuditEvent;
import com.shreeya.vehicletitle.domain.TransactionStatus;

import java.time.Instant;

public record AuditEventResponse(
        long id,
        String eventType,
        TransactionStatus fromStatus,
        TransactionStatus toStatus,
        SubmissionChannel channel,
        String actor,
        String details,
        Instant occurredAt
) {
    static AuditEventResponse from(TransactionAuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getEventType(), event.getFromStatus(),
                event.getToStatus(), event.getChannel(), event.getActor(), event.getDetails(),
                event.getOccurredAt());
    }
}
