package com.financialtransaction.user.dto;

import com.financialtransaction.user.entity.User;

public record UserResponse(Long id, String name, String email, String status) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getStatus().name());
    }
}
