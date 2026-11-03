package com.financialtransaction.reconciliation.controller;

import com.financialtransaction.reconciliation.dto.ReconciliationReport;
import com.financialtransaction.reconciliation.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/reconciliation")
@Tag(
        name = "Admin - Reconciliation",
        description = "Administrative APIs for verifying financial transaction and ledger consistency."
)
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(
            ReconciliationService reconciliationService
    ) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/transfers")
    @Operation(
            summary = "Reconcile Transfers",
            description = """
                Runs reconciliation checks on financial transfers
                and their corresponding ledger entries.
                Returns a reconciliation report containing the results.
                """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reconciliation completed successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Administrative access required"),
            @ApiResponse(responseCode = "500", description = "Reconciliation processing failed")
    })
    public ReconciliationReport reconcileTransfers() {
        return reconciliationService.reconcileTransfers();
    }

}
