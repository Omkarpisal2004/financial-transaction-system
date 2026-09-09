package com.financialtransaction.account.dto;

import java.math.BigDecimal;

public record AccountCreateRequest(Long userId, String accountNumber, BigDecimal initialBalance) {
}
