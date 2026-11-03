package com.financialtransaction.user.dto;

import com.financialtransaction.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User details returned by the system")
public record UserResponse(

        @Schema(
                description = "Unique user identifier",
                example = "46"
        )
        Long id,

        @Schema(
                description = "Full name of the user",
                example = "Rahul Sharma"
        )
        String name,

        @Schema(
                description = "Registered email address",
                example = "rahul@example.com"
        )
        String email,

        @Schema(
                description = "Current status of the user account",
                example = "ACTIVE"
        )
        String status

) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getStatus().name()
        );
    }
}