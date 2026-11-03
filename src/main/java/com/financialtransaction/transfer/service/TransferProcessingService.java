package com.financialtransaction.transfer.service;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.audit.event.TransferCompleteEvent;
import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.dto.TransferResponse;
import com.financialtransaction.transfer.entity.Transfer;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.repository.TransferRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferProcessingService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransferProcessingService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TransferResponse processTransfer(
            String transactionId,
            TransferRequest request,
            Long currentUserId
    ) {

        // Fetch existing PENDING transfer.
        Transfer transfer = transferRepository
                .findByTransactionId(transactionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transfer not found: " + transactionId
                        )
                );

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING transfers can be processed"
            );
        }

        // Request validation.
        if (request.fromAccountId() == null
                || request.toAccountId() == null) {
            throw new IllegalArgumentException("Account IDs are required");
        }

        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new IllegalArgumentException(
                    "Cannot transfer to the same account"
            );
        }

        if (request.amount() == null) {
            throw new IllegalArgumentException(
                    "Transfer amount is required"
            );
        }

        if (request.amount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Transfer amount must be positive"
            );
        }

        if (request.amount().scale() > 2
                || request.amount().precision()
                - request.amount().scale() > 15) {

            throw new IllegalArgumentException(
                    "Transfer amount exceeds supported precision"
            );
        }

        // Ensure request matches the persisted transfer.
        if (!transfer.getFromAccount().getId()
                .equals(request.fromAccountId())
                || !transfer.getToAccount().getId()
                .equals(request.toAccountId())
                || transfer.getAmount().compareTo(request.amount()) != 0) {

            throw new IllegalArgumentException(
                    "Transfer details do not match the initiated transfer"
            );
        }

        // Lock accounts in ascending ID order to prevent deadlocks.
        Long firstLockId = Math.min(
                request.fromAccountId(),
                request.toAccountId()
        );

        Long secondLockId = Math.max(
                request.fromAccountId(),
                request.toAccountId()
        );

        Account firstLocked = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + firstLockId
                        )
                );

        Account secondLocked = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + secondLockId
                        )
                );

        Account fromAccount = firstLocked.getId()
                .equals(request.fromAccountId())
                ? firstLocked : secondLocked;

        Account toAccount = firstLocked.getId()
                .equals(request.toAccountId())
                ? firstLocked : secondLocked;

        // Ownership validation.
        if (!fromAccount.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException(
                    "You do not have permission to transfer from this account"
            );
        }

        // Account status validation.
        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Source account is not active"
            );
        }

        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Destination account is not active"
            );
        }

        // Currency validation.
        if (fromAccount.getCurrency() != toAccount.getCurrency()) {
            throw new IllegalArgumentException(
                    "Source and destination currencies must match"
            );
        }

        // Balance validation.
        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient balance in account: "
                            + fromAccount.getAccountNumber()
            );
        }

        // Debit source account.
        fromAccount.setBalance(
                fromAccount.getBalance().subtract(request.amount())
        );

        // Credit destination account.
        toAccount.setBalance(
                toAccount.getBalance().add(request.amount())
        );

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Update existing transfer to SUCCESS.
        transfer.setStatus(TransferStatus.SUCCESS);

        Transfer savedTransfer = transferRepository.save(transfer);

        // Create DEBIT ledger entry.
        LedgerEntry debitEntry = new LedgerEntry();

        debitEntry.setTransfer(savedTransfer);
        debitEntry.setAccount(fromAccount);
        debitEntry.setEntryType(EntryType.DEBIT);
        debitEntry.setAmount(request.amount());

        ledgerEntryRepository.save(debitEntry);

        // Create CREDIT ledger entry.
        LedgerEntry creditEntry = new LedgerEntry();

        creditEntry.setTransfer(savedTransfer);
        creditEntry.setAccount(toAccount);
        creditEntry.setEntryType(EntryType.CREDIT);
        creditEntry.setAmount(request.amount());

        ledgerEntryRepository.save(creditEntry);

        // Event listener executes AFTER_COMMIT.
        eventPublisher.publishEvent(
                new TransferCompleteEvent(
                        currentUserId,
                        savedTransfer.getTransactionId(),
                        "Transferred " + request.amount()
                                + " from account " + fromAccount.getId()
                                + " to " + toAccount.getId()
                )
        );

        return TransferResponse.from(savedTransfer);
    }
}

