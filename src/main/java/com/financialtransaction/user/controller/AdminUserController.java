package com.financialtransaction.user.controller;

import com.financialtransaction.user.dto.UserResponse;
import com.financialtransaction.user.dto.UserStatusUpdateRequest;
import com.financialtransaction.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "Admin - User Management",
        description = "Administrative APIs for managing user accounts."
)
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Update User Status",
            description = """
                Updates the status of an existing user.
                This endpoint is restricted to users with the ADMIN role.
                """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status update request"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> updateUserStatus(

            @Parameter(
                    description = "ID of the user whose status needs to be updated",
                    example = "46"
            )
            @PathVariable Long id,

            @Valid @RequestBody UserStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(
                userService.updateUserStatus(id, request)
        );
    }
}
