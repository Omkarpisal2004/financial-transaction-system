package com.financialtransaction.account.dto;

import com.financialtransaction.account.entity.Account;

import java.math.BigDecimal;

public record AccountResponse(Long id, String accountNumber, BigDecimal balance, String currency, String status) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency().name(),
                account.getStatus().name()
        );
    }
}