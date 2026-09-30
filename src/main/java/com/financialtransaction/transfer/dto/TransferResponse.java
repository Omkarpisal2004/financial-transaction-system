package com.financialtransaction.transfer.dto;

import com.financialtransaction.transfer.entity.Transfer;

import java.math.BigDecimal;

public record TransferResponse(
        String transactionId,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String status
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getTransactionId(),
                transfer.getFromAccount().getId(),
                transfer.getToAccount().getId(),
                transfer.getAmount(),
                transfer.getStatus().name()
        );
    }
}
