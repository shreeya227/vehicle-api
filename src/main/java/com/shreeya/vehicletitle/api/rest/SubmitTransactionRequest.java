package com.shreeya.vehicletitle.api.rest;

import com.shreeya.vehicletitle.application.OwnerCommand;
import com.shreeya.vehicletitle.application.SubmitTitleCommand;
import com.shreeya.vehicletitle.domain.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record SubmitTransactionRequest(
        @NotBlank @Pattern(regexp = "[A-HJ-NPR-Za-hj-npr-z0-9]{17}") String vin,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String jurisdiction,
        @NotNull TransactionType transactionType,
        @NotNull @Valid OwnerRequest owner,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal purchasePrice
) {
    public SubmitTitleCommand toCommand() {
        OwnerCommand ownerCommand = new OwnerCommand(owner.fullName(), owner.email(),
                owner.addressLine1(), owner.city(), owner.state(), owner.postalCode());
        return new SubmitTitleCommand(vin, jurisdiction, transactionType, ownerCommand, purchasePrice);
    }
}
