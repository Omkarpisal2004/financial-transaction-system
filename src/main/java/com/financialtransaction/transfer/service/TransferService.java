package com.financialtransaction.transfer.service;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.dto.TransferResponse;
import com.financialtransaction.transfer.entity.Transfer;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransferService(AccountRepository accountRepository,
                           TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public TransferResponse createTransfer(TransferRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        Account fromAccount = accountRepository.findById(request.fromAccountId())
                .orElseThrow(() -> new IllegalArgumentException("From account not found: " + request.fromAccountId()));
        Account toAccount = accountRepository.findById(request.toAccountId())
                .orElseThrow(() -> new IllegalArgumentException("To account not found: " + request.toAccountId()));

        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient balance in account: " + fromAccount.getAccountNumber());
        }

        // Debit
        fromAccount.setBalance(fromAccount.getBalance().subtract(request.amount()));
        accountRepository.save(fromAccount);

        // Credit
        toAccount.setBalance(toAccount.getBalance().add(request.amount()));
        accountRepository.save(toAccount);

        // Create Transfer record
        Transfer transfer = new Transfer();
        transfer.setTransactionId(UUID.randomUUID().toString());
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setAmount(request.amount());
        transfer.setStatus(TransferStatus.SUCCESS);
        Transfer savedTransfer = transferRepository.save(transfer);

        // Create Ledger Entries — exactly 1 DEBIT + 1 CREDIT
        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setTransfer(savedTransfer);
        debitEntry.setAccount(fromAccount);
        debitEntry.setEntryType(EntryType.DEBIT);
        debitEntry.setAmount(request.amount());
        ledgerEntryRepository.save(debitEntry);

        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setTransfer(savedTransfer);
        creditEntry.setAccount(toAccount);
        creditEntry.setEntryType(EntryType.CREDIT);
        creditEntry.setAmount(request.amount());
        ledgerEntryRepository.save(creditEntry);

        return TransferResponse.from(savedTransfer);
    }

    public TransferResponse getByTransactionId(String transactionId) {
        Transfer transfer = transferRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transactionId));
        return TransferResponse.from(transfer);
    }
}