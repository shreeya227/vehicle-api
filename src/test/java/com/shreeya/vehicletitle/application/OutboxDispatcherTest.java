package com.shreeya.vehicletitle.application;

import com.shreeya.vehicletitle.domain.OutboxEvent;
import com.shreeya.vehicletitle.domain.OutboxStatus;
import com.shreeya.vehicletitle.infrastructure.persistence.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {

    @Test
    void recordsRetryMetadataWhenPublicationFails() {
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(),
                "TITLE_TRANSACTION_RECEIVED", "{}", now);
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        when(repository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxStatus.PENDING, now)).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new IllegalStateException("gateway unavailable"))
                .when(publisher).publish(event);
        OutboxDispatcher dispatcher = new OutboxDispatcher(repository, publisher,
                Clock.fixed(now, ZoneOffset.UTC));

        dispatcher.publishPendingEvents();

        verify(repository).findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), eq(now));
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(2));
    }
}
