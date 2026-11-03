package com.financialtransaction.reconciliation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Report containing transfer and account reconciliation results")
public record ReconciliationReport(

@Schema(
        description = "Timestamp when reconciliation was executed",
        example = "2026-11-02T10:30:00Z"
)
Instant checkedAt,

@Schema(
        description = "Total number of transfers checked",
        example = "100"
)
int transfersChecked,

@Schema(
        description = "Number of transfers that passed reconciliation",
        example = "98"
)
int reconciledTransfers,

@Schema(
        description = "Number of transfers with inconsistencies",
        example = "2"
)
int inconsistentTransfers,

@Schema(
        description = "Details of transfers that failed reconciliation"
)
List<TransferReconciliationItem> discrepancies,

@Schema(
        description = "Total number of accounts checked",
        example = "50"
)
int accountsChecked,

@Schema(
        description = "Number of accounts that passed reconciliation",
        example = "48"
)
int reconciledAccounts,

@Schema(
        description = "Number of accounts with inconsistencies",
        example = "2"
)
int inconsistentAccounts,

@Schema(
        description = "Details of accounts that failed reconciliation"
)
List<AccountReconciliationItem> accountDiscrepancies
) {
    public int totalSuccessfulTransfers() {
        return transfersChecked;
    }
}
