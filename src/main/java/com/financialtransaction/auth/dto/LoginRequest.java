package com.financialtransaction.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credentials required to authenticate a registered user")
public record LoginRequest(

        @Schema(
                description = "Registered user's email address",
                example = "user@example.com",
                format = "email",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String email,

        @Schema(
                description = "User's account password",
                example = "your-password",
                format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String password
) {
}