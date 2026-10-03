package com.financialtransaction.transfer.service;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.Currency;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.audit.service.AuditService;
import com.financialtransaction.idempotency.repository.IdempotencyRecordRepository;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.repository.TransferRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransferRepository transferRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock private AuditService auditService;

    private TransferService transferService;

    @BeforeEach
    void setup() {
        transferService = new TransferService(
                accountRepository, transferRepository, ledgerEntryRepository,
                idempotencyRecordRepository, auditService, new SimpleMeterRegistry());

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private Account buildAccount(Long id, String accNo, BigDecimal balance) {
        Account account = new Account();
        account.setId(id);
        account.setAccountNumber(accNo);
        account.setBalance(balance);
        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    @Test
    void shouldThrowException_whenAmountIsNegative() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("-100"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transferService.createTransfer(request));

        assertEquals("Transfer amount must be positive", ex.getMessage());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldThrowException_whenSameAccountTransfer() {
        TransferRequest request = new TransferRequest(1L, 1L, new BigDecimal("100"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transferService.createTransfer(request));

        assertEquals("Cannot transfer to the same account", ex.getMessage());
    }

    @Test
    void shouldThrowException_whenInsufficientBalance() {
        Account fromAccount = buildAccount(1L, "ACC001", new BigDecimal("100"));
        Account toAccount = buildAccount(2L, "ACC002", new BigDecimal("0"));

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(toAccount));

        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("500"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transferService.createTransfer(request));

        assertTrue(ex.getMessage().contains("Insufficient balance"));
    }

    @Test
    void shouldThrowException_whenFromAccountNotFound() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("100"));

        assertThrows(IllegalArgumentException.class, () -> transferService.createTransfer(request));
    }
}