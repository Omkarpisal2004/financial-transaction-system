package com.financialtransaction.account.dto;

import com.financialtransaction.account.entity.Account;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Bank account details returned by the API")
public record AccountResponse(

        @Schema(description = "Unique account ID", example = "159")
        Long id,

        @Schema(description = "System-generated account number", example = "123456789012")
        String accountNumber,

        @Schema(description = "Current account balance", example = "10000.00")
        BigDecimal balance,

        @Schema(description = "Account currency", example = "INR")
        String currency,

        @Schema(description = "Type of account", example = "SAVINGS")
        String accountType,

        @Schema(description = "Current account status", example = "ACTIVE")
        String status

) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency().name(),
                account.getAccountType().name(),
                account.getStatus().name()
        );
    }
}