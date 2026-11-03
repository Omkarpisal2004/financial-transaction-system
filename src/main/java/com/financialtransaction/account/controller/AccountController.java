package com.financialtransaction.account.controller;

import com.financialtransaction.account.dto.AccountCreateRequest;
import com.financialtransaction.account.dto.AccountResponse;
import com.financialtransaction.account.service.AccountService;
import com.financialtransaction.ledger.dto.LedgerEntryResponse;
import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.ledger.specification.LedgerEntrySpecification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/accounts")
@Tag(
        name = "Account Management",
        description = "APIs for creating accounts, retrieving account details, checking balances and viewing transaction history."
)
public class AccountController {
    private final AccountService accountService;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountController(AccountService accountService, LedgerEntryRepository ledgerEntryRepository) {
        this.accountService = accountService;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @PostMapping
    @Operation(
            summary = "Create Account",
            description = "Creates a new bank account for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid account creation request"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request
    ) {
        AccountResponse response = accountService.createAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get Account Details",
            description = "Retrieves account details by account ID. Access is subject to account ownership authorization."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account details retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "Unique account ID", example = "159")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/{id}/balance")
    @Operation(
            summary = "Get Account Balance",
            description = "Retrieves the current balance of an account."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account balance retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<BigDecimal> getBalance(
            @Parameter(description = "Unique account ID", example = "159")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(accountService.getBalance(id));
    }

    @GetMapping("/{id}/transactions")
    @Operation(
            summary = "Get Account Transaction History",
            description = "Retrieves paginated ledger entries for an account. Supports filtering by entry type and date range, along with sorting."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction history retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or filter parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<Page<LedgerEntryResponse>> getTransactions(

            @Parameter(description = "Unique account ID", example = "159")
            @PathVariable Long id,

            @Parameter(description = "Page number (zero-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of records per page", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Field used for sorting", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Sort direction: asc or desc", example = "desc")
            @RequestParam(defaultValue = "desc") String direction,

            @Parameter(description = "Filter by ledger entry type: DEBIT or CREDIT", example = "DEBIT")
            @RequestParam(required = false) EntryType entryType,

            @Parameter(description = "Filter entries created after this date/time (ISO format)", example = "2026-10-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @Parameter(description = "Filter entries created before this date/time (ISO format)", example = "2026-10-31T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate
    ) {
        accountService.verifyAccountAccess(id);

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

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
