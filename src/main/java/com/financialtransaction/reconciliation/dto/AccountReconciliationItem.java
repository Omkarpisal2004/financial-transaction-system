package com.financialtransaction.reconciliation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Reconciliation result for an individual bank account")
public record AccountReconciliationItem(

        @Schema(
                description = "Unique account ID",
                example = "159"
        )
        Long accountId,

        @Schema(
                description = "Current account balance stored in the accounts table",
                example = "10000.00"
        )
        BigDecimal actualBalance,

        @Schema(
                description = "Account balance calculated from ledger entries",
                example = "10000.00"
        )
        BigDecimal ledgerBalance,

        @Schema(
                description = "Indicates whether the account balance matches its ledger balance",
                example = "true"
        )
        boolean reconciled,

        @Schema(
                description = "List of detected account reconciliation discrepancies",
                example = "[]"
        )
        List<String> discrepancies

) {
}