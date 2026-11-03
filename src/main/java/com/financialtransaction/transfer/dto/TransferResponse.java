package com.financialtransaction.transfer.dto;

import com.financialtransaction.transfer.entity.Transfer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Response containing the details of a financial transfer")
public record TransferResponse(

        @Schema(
                description = "Unique transaction identifier",
                example = "TXN-2026-001"
        )
        String transactionId,

        @Schema(
                description = "Source account ID",
                example = "159"
        )
        Long fromAccountId,

        @Schema(
                description = "Destination account ID",
                example = "160"
        )
        Long toAccountId,

        @Schema(
                description = "Transferred amount",
                example = "200.00"
        )
        BigDecimal amount,

        @Schema(
                description = "Current transfer status",
                example = "SUCCESS"
        )
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