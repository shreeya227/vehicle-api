package com.shreeya.vehicletitle.infrastructure.persistence;

import com.shreeya.vehicletitle.domain.TransactionAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionAuditRepository extends JpaRepository<TransactionAuditEvent, Long> {

    List<TransactionAuditEvent> findByTransactionIdOrderByOccurredAtAsc(UUID transactionId);
}
