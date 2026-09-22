package com.shreeya.vehicletitle.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TitleTransactionTest {

    @Test
    void enforcesTheTitleLifecycle() {
        TitleTransaction transaction = transaction();
        Instant later = Instant.parse("2026-09-22T12:01:00Z");

        TransactionStatus previous = transaction.transitionTo(
                TransactionStatus.VALIDATING, null, null, later);

        assertThat(previous).isEqualTo(TransactionStatus.RECEIVED);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.VALIDATING);
        assertThat(transaction.getUpdatedAt()).isEqualTo(later);
        assertThatThrownBy(() -> transaction.transitionTo(
                TransactionStatus.COMPLETED, "MA-123", null, later))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("VALIDATING")
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void requiresAReasonWhenRejectingATransaction() {
        TitleTransaction transaction = transaction();

        assertThatThrownBy(() -> transaction.transitionTo(
                TransactionStatus.REJECTED, null, " ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejection reason");
    }

    private TitleTransaction transaction() {
        return TitleTransaction.create(UUID.randomUUID(), "title-key-001", "a".repeat(64),
                "1HGCM82633A004352", "MA", TransactionType.TITLE_TRANSFER,
                new OwnerDetails("Alex Morgan", "alex@example.com", "1 Main Street",
                        "Boston", "MA", "02108"), new BigDecimal("18450.00"),
                Instant.parse("2026-09-22T12:00:00Z"));
    }
}
