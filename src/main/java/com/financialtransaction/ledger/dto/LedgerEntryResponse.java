package com.financialtransaction.ledger.dto;

import com.financialtransaction.ledger.entity.LedgerEntry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerEntryResponse(
        Long id,
        String transactionId,
        Long accountId,
        String entryType,
        BigDecimal amount,
        LocalDateTime createdAt
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransfer().getTransactionId(),
                entry.getAccount().getId(),
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getCreatedAt()
        );
    }
}
