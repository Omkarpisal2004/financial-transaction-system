package com.financialtransaction.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for registering a new user")
public record UserCreateRequest(

        @Schema(
                description = "Full name of the user",
                example = "Rahul Sharma",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(
                description = "Unique email address of the user",
                example = "rahul@example.com",
                format = "email"
        )
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Schema(
                description = "Password for the user account (8-72 characters)",
                example = "StrongPassword123",
                format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password

) {
}