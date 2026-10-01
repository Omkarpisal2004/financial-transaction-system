package com.financialtransaction.account.controller;

import com.financialtransaction.account.dto.AccountCreateRequest;
import com.financialtransaction.account.dto.AccountResponse;
import com.financialtransaction.account.service.AccountService;
import com.financialtransaction.ledger.dto.LedgerEntryResponse;
import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.ledger.specification.LedgerEntrySpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountController(AccountService accountService, LedgerEntryRepository ledgerEntryRepository) {
        this.accountService = accountService;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountCreateRequest request) {
        return ResponseEntity.status(201).body(accountService.createAccount(request));
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
    public ResponseEntity<Page<LedgerEntryResponse>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) EntryType entryType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<LedgerEntry> spec = Specification
                .where(LedgerEntrySpecification.hasAccountId(id))
                .and(LedgerEntrySpecification.hasEntryType(entryType))
                .and(LedgerEntrySpecification.createdAfter(startDate))
                .and(LedgerEntrySpecification.createdBefore(endDate));

        Page<LedgerEntryResponse> result = ledgerEntryRepository.findAll(spec, pageable)
                .map(LedgerEntryResponse::from);

        return ResponseEntity.ok(result);
    }
}