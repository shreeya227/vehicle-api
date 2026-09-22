package com.shreeya.vehicletitle.application;

import com.shreeya.vehicletitle.domain.OutboxStatus;
import com.shreeya.vehicletitle.infrastructure.persistence.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class OutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int MAXIMUM_ATTEMPTS = 5;

    private final OutboxEventRepository repository;
    private final OutboxPublisher publisher;
    private final Clock clock;

    @Autowired
    public OutboxDispatcher(OutboxEventRepository repository, OutboxPublisher publisher) {
        this(repository, publisher, Clock.systemUTC());
    }

    OutboxDispatcher(OutboxEventRepository repository, OutboxPublisher publisher, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}",
            initialDelayString = "${app.outbox.initial-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        Instant now = clock.instant();
        var dueEvents = repository
                .findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, now);
        for (var event : dueEvents) {
            try {
                publisher.publish(event);
                event.markPublished(now);
            } catch (RuntimeException exception) {
                event.recordFailure(now, MAXIMUM_ATTEMPTS);
                LOGGER.warn("Outbox delivery failed for event {} on attempt {}",
                        event.getId(), event.getAttempts(), exception);
            }
        }
    }
}
