package com.financialtransaction.audit.event;

public record TransferCompleteEvent(
        Long userId,
        String transactionId,
        String details
) {
}