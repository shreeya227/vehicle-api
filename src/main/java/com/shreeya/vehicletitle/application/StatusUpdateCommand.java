package com.shreeya.vehicletitle.application;

import com.shreeya.vehicletitle.domain.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateCommand(
        @NotNull TransactionStatus status,
        @Size(max = 100) String externalReference,
        @Size(max = 500) String rejectionReason
) {
}
