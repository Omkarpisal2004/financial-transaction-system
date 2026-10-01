package com.financialtransaction.auth.dto;

public record LoginResponse(String token, Long userId, String email) {
}
