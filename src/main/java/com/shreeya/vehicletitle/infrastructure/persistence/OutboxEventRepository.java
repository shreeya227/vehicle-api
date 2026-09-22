package com.shreeya.vehicletitle.infrastructure.persistence;

import com.shreeya.vehicletitle.domain.OutboxEvent;
import com.shreeya.vehicletitle.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus status, Instant now);
}
