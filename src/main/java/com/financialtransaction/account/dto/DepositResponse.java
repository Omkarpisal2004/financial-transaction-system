package com.financialtransaction.account.dto;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.AccountType;
import com.financialtransaction.account.entity.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Response containing deposit details and updated account information")
public record DepositResponse(

        @Schema(description = "Unique account ID", example = "159")
        Long accountId,

        @Schema(description = "Account number", example = "123456789012")
        String accountNumber,

        @Schema(description = "Amount deposited", example = "5000.00")
        BigDecimal depositedAmount,

        @Schema(description = "Account balance after successful deposit", example = "15000.00")
        BigDecimal balance,

        @Schema(description = "Account currency", example = "INR")
        Currency currency,

        @Schema(description = "Type of bank account", example = "SAVINGS")
        AccountType accountType,

        @Schema(description = "Current account status", example = "ACTIVE")
        AccountStatus status

) {
    public static DepositResponse from(Account account, BigDecimal depositedAmount) {
        return new DepositResponse(
                account.getId(),
                account.getAccountNumber(),
                depositedAmount,
                account.getBalance(),
                account.getCurrency(),
                account.getAccountType(),
                account.getStatus()
        );
    }
}