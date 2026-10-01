package com.financialtransaction.transfer.service;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.idempotency.entity.IdempotencyRecord;
import com.financialtransaction.idempotency.entity.IdempotencyStatus;
import com.financialtransaction.idempotency.repository.IdempotencyRecordRepository;
import com.financialtransaction.idempotency.util.HashUtil;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransferService(AccountRepository accountRepository,
                           TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           IdempotencyRecordRepository idempotencyRecordRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }


    @Transactional
    public TransferResponse createTransferIdempotent(String idempotencyKey, TransferRequest request) {
        // If no key provided, just process normally (idempotency optional)
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return createTransfer(request);
        }

        String requestPayload;
        try {
            requestPayload = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
        String requestHash = HashUtil.sha256(requestPayload);

        var existingOpt = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IllegalArgumentException(
                        "Idempotency key already used with a different request payload: " + idempotencyKey);
            }

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                try {
                    return objectMapper.readValue(existing.getResponseBody(), TransferResponse.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize stored response", e);
                }
            }

            // IN_PROGRESS or FAILED — for simplicity, reject concurrent duplicate while in progress
            throw new IllegalStateException("Request with this idempotency key is already being processed: " + idempotencyKey);
        }

        // New key — create a record in IN_PROGRESS state first
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        idempotencyRecordRepository.save(record);

        TransferResponse response = createTransfer(request);

        try {
            record.setResponseBody(objectMapper.writeValueAsString(response));
            record.setStatus(IdempotencyStatus.COMPLETED);
            idempotencyRecordRepository.save(record);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response for idempotency record", e);
        }

        return response;
    }


    @Transactional
    public TransferResponse createTransfer(TransferRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        // LOCK ORDERING: always lock the lower ID first, to prevent deadlocks
        Long firstLockId = Math.min(request.fromAccountId(), request.toAccountId());
        Long secondLockId = Math.max(request.fromAccountId(), request.toAccountId());

        Account firstLocked = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstLockId));
        Account secondLocked = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondLockId));

        // Now resolve which is "from" and which is "to"
        Account fromAccount = firstLocked.getId().equals(request.fromAccountId()) ? firstLocked : secondLocked;
        Account toAccount = firstLocked.getId().equals(request.toAccountId()) ? firstLocked : secondLocked;

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