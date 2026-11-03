package com.financialtransaction.transfer.controller;

import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.dto.TransferResponse;
import com.financialtransaction.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/transfers")
@Tag(
        name = "Transfer Management",
        description = "APIs for creating and retrieving financial transfers."
)
public class TransferController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(
            summary = "Create Money Transfer",
            description = """
                Creates a financial transfer between two accounts.
                Requires an Idempotency-Key header to prevent duplicate
                transfers when the same request is retried.
                Reusing the same key with a different request payload is rejected.
                """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or Idempotency-Key"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict")
    })
    public ResponseEntity<TransferResponse> createTransfer(

            @Parameter(
                    name = "Idempotency-Key",
                    description = "Unique key for identifying this transfer request. Must contain 1 to 100 characters. Reuse the same key when retrying the same request.",
                    example = "transfer-2026-001",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody TransferRequest request) {

        validateIdempotencyKey(idempotencyKey);

        TransferResponse response =
                transferService.createTransferIdempotent(
                        idempotencyKey,
                        request
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{transactionId}")
    @Operation(
            summary = "Get Transfer Details",
            description = "Retrieves transfer details using the unique transaction ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer details retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Transfer not found")
    })
    public ResponseEntity<TransferResponse> getTransfer(

            @Parameter(
                    description = "Unique transaction ID",
                    example = "TXN-2026-001"
            )
            @PathVariable String transactionId) {

        return ResponseEntity.ok(
                transferService.getByTransactionId(transactionId)
        );
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()
                || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {

            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 1 to 100 characters"
            );
        }
    }

}
