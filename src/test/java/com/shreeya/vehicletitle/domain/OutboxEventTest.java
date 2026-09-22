package com.shreeya.vehicletitle.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void backsOffAndEventuallyMarksRepeatedFailuresAsTerminal() {
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(),
                "TITLE_TRANSACTION_RECEIVED", "{}", now);

        event.recordFailure(now, 5);

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(2));

        for (int attempt = 2; attempt <= 5; attempt++) {
            event.recordFailure(now, 5);
        }
        assertThat(event.getAttempts()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
