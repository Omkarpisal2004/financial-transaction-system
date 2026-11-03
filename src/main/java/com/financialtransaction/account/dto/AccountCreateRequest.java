package com.financialtransaction.account.dto;

import com.financialtransaction.account.entity.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload for creating a new bank account")
public record AccountCreateRequest(

        @Schema(
                description = "Type of bank account to create",
                example = "SAVINGS",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Account type is required")
        AccountType accountType
) {
}