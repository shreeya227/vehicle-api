package com.shreeya.vehicletitle.api.rest;

import com.shreeya.vehicletitle.application.StatusUpdateCommand;
import com.shreeya.vehicletitle.domain.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull TransactionStatus status,
        @Size(max = 100) String externalReference,
        @Size(max = 500) String rejectionReason
) {
    public StatusUpdateCommand toCommand() {
        return new StatusUpdateCommand(status, externalReference, rejectionReason);
    }
}
