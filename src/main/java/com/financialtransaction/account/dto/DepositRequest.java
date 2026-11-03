package com.financialtransaction.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request payload for depositing money into a bank account")
public record DepositRequest(

        @Schema(
                description = "Amount to deposit. Must be greater than zero and can have up to 4 decimal places.",
                example = "5000.00",
                minimum = "0.0001",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Deposit amount is required")
        @DecimalMin(
                value = "0",
                inclusive = false,
                message = "Deposit amount must be greater than zero"
        )
        @Digits(
                integer = 15,
                fraction = 4,
                message = "Deposit amount must have at most 4 decimal places"
        )
        BigDecimal amount

) {}