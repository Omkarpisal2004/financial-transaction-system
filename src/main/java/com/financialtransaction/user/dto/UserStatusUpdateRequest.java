package com.financialtransaction.user.dto;

import com.financialtransaction.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload for updating a user's account status")
public record UserStatusUpdateRequest(

        @Schema(
                description = "New status to assign to the user",
                example = "BLOCKED",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "User status is required")
        UserStatus status

) {
}