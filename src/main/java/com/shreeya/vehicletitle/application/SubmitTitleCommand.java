package com.shreeya.vehicletitle.application;

import com.shreeya.vehicletitle.domain.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record SubmitTitleCommand(
        @NotBlank @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}") String vin,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String jurisdiction,
        @NotNull TransactionType transactionType,
        @NotNull @Valid OwnerCommand owner,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal purchasePrice
) {
}
