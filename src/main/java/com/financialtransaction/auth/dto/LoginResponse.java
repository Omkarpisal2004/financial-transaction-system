package com.financialtransaction.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response returned after successful authentication")
public record LoginResponse(

        @Schema(
                description = "JWT access token used to authenticate protected API requests",
                example = "eyJhbGciOiJIUzI1NiJ9..."
        )
        String token,

        @Schema(
                description = "Unique identifier of the authenticated user",
                example = "46"
        )
        Long userId,

        @Schema(
                description = "Email address of the authenticated user",
                example = "user@example.com"
        )
        String email
) {
}