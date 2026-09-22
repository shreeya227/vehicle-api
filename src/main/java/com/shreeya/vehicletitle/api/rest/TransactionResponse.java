package com.shreeya.vehicletitle.api.rest;

import com.shreeya.vehicletitle.domain.TitleTransaction;
import com.shreeya.vehicletitle.domain.TransactionStatus;
import com.shreeya.vehicletitle.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String vin,
        String jurisdiction,
        TransactionType transactionType,
        OwnerResponse owner,
        BigDecimal purchasePrice,
        TransactionStatus status,
        String externalReference,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransactionResponse from(TitleTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getVin(),
                transaction.getJurisdiction(), transaction.getTransactionType(),
                OwnerResponse.from(transaction.getOwner()), transaction.getPurchasePrice(),
                transaction.getStatus(), transaction.getExternalReference(),
                transaction.getRejectionReason(), transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
