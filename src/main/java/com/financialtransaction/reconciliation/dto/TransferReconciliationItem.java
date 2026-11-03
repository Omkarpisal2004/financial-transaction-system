package com.financialtransaction.reconciliation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Reconciliation result for an individual financial transfer")
public record TransferReconciliationItem(

        @Schema(description = "Unique database ID of the transfer", example = "125")
        Long transferId,

        @Schema(description = "Unique transaction identifier", example = "TXN-2026-001")
        String transactionId,

        @Schema(description = "Current transfer status", example = "SUCCESS")
        String status,

        @Schema(description = "Expected transfer amount", example = "200.00")
        BigDecimal expectedAmount,

        @Schema(description = "Actual number of ledger entries associated with the transfer", example = "2")
        int actualLedgerEntryCount,

        @Schema(description = "Number of debit ledger entries", example = "1")
        int debitCount,

        @Schema(description = "Number of credit ledger entries", example = "1")
        int creditCount,

        @Schema(description = "Indicates whether the transfer passed reconciliation", example = "true")
        boolean reconciled,

        @Schema(description = "List of detected reconciliation discrepancies")
        List<String> discrepancies

) {
}