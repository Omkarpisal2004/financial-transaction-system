package com.financialtransaction.account.controller;

import com.financialtransaction.account.dto.AccountCreateRequest;
import com.financialtransaction.account.dto.AccountResponse;
import com.financialtransaction.account.service.AccountService;
import com.financialtransaction.ledger.dto.LedgerEntryResponse;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountController(AccountService accountService , LedgerEntryRepository ledgerEntryRepository) {
        this.accountService = accountService;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountCreateRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getBalance(id));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<LedgerEntryResponse>> getTransactions(@PathVariable Long id) {
        List<LedgerEntryResponse> entries = ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(id)
                .stream()
                .map(LedgerEntryResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(entries);
    }

}