package com.financialtransaction.account.controller;

import com.financialtransaction.account.dto.DepositRequest;
import com.financialtransaction.account.dto.DepositResponse;
import com.financialtransaction.account.service.DepositService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts")
@Tag(
        name = "Account Management",
        description = "APIs for account operations, including deposits."
)
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/{accountId}/deposit")
    @Operation(
            summary = "Deposit Money",
            description = "Deposits money into an existing bank account."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Deposit completed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid deposit request"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found"
            )
    })
    public ResponseEntity<DepositResponse> deposit(

            @Parameter(
                    description = "ID of the account receiving the deposit",
                    example = "159"
            )
            @PathVariable Long accountId,

            @Valid @RequestBody DepositRequest request
    ) {
        DepositResponse response =
                depositService.deposit(accountId, request);

        return ResponseEntity.ok(response);
    }
}