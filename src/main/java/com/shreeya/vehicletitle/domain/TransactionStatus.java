package com.shreeya.vehicletitle.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TransactionStatus {
    RECEIVED,
    VALIDATING,
    SUBMITTED_TO_STATE,
    COMPLETED,
    REJECTED;

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS = Map.of(
            RECEIVED, EnumSet.of(VALIDATING, REJECTED),
            VALIDATING, EnumSet.of(SUBMITTED_TO_STATE, REJECTED),
            SUBMITTED_TO_STATE, EnumSet.of(COMPLETED, REJECTED),
            COMPLETED, EnumSet.noneOf(TransactionStatus.class),
            REJECTED, EnumSet.noneOf(TransactionStatus.class)
    );

    public boolean canTransitionTo(TransactionStatus next) {
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }
}
