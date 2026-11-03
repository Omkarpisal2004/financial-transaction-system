package com.financialtransaction.account.service;

import com.financialtransaction.account.dto.DepositRequest;
import com.financialtransaction.account.dto.DepositResponse;
import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.audit.service.AuditService;
import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.security.userdetails.UserPrincipal;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class DepositService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;

    public DepositService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AuditService auditService
    ) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DepositResponse deposit(Long accountId, DepositRequest request) {

        // 1. Validate the deposit request
        if (request == null || request.amount() == null) {
            throw new IllegalArgumentException("Deposit amount is required");
        }

        BigDecimal amount = request.amount();

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Deposit amount must be greater than zero"
            );
        }

        if (amount.scale() > 0) {
            throw new IllegalArgumentException(
                    "Deposit amount cannot have more than 4 decimal places"
            );
        }

        // 2. Get the authenticated user
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException(
                    "Authenticated user is required to deposit money"
            );
        }

        Long currentUserId = principal.getId();

        // 3. Lock the account before updating its balance
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + accountId
                        )
                );

        // 4. Verify account ownership
        if (account.getUser() == null
                || !account.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException(
                    "You do not have permission to deposit into this account"
            );
        }

        // 5. Only active accounts can receive deposits
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Deposits are allowed only for active accounts"
            );
        }

        // 6. Calculate the updated balance
        BigDecimal currentBalance = account.getBalance();
        BigDecimal updatedBalance = currentBalance.add(amount);

        account.setBalance(updatedBalance);

        Account savedAccount = accountRepository.save(account);

        // 7. Create the credit ledger entry
        LedgerEntry ledgerEntry = new LedgerEntry();

        ledgerEntry.setAccount(savedAccount);
        ledgerEntry.setTransfer(null);
        ledgerEntry.setEntryType(EntryType.CREDIT);
        ledgerEntry.setAmount(amount);

        ledgerEntryRepository.save(ledgerEntry);

        // 8. Record the audit event
        auditService.log(
                currentUserId,
                "DEPOSIT_COMPLETED",
                null,
                "SUCCESS",
                "Deposited " + amount
                        + " " + savedAccount.getCurrency()
                        + " into account ID " + savedAccount.getId()
        );

        // 9. Return the deposit result
        return DepositResponse.from(savedAccount, amount);
    }
}