package com.financialtransaction.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Request payload for transferring money between two accounts")
public record TransferRequest(

        @Schema(
                description = "ID of the source account from which money will be debited",
                example = "159",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Source account ID is required")
        @Positive(message = "Source account ID must be positive")
        Long fromAccountId,

        @Schema(
                description = "ID of the destination account receiving the money",
                example = "160",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Destination account ID is required")
        @Positive(message = "Destination account ID must be positive")
        Long toAccountId,

        @Schema(
                description = "Amount to transfer. Must be greater than zero.",
                example = "200.00",
                minimum = "0.01",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Transfer amount is required")
        @DecimalMin(value = "0.0", inclusive = false,
                message = "Transfer amount must be greater than zero")
        @Digits(integer = 15, fraction = 2,
                message = "Transfer amount must have at most 4 decimal places")
        BigDecimal amount

) {}