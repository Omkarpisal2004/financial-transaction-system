package com.financialtransaction.transfer.service;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.dto.TransferResponse;
import com.financialtransaction.transfer.entity.Transfer;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.repository.TransferRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.math.BigDecimal;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
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
        // Credit
        toAccount.setBalance(toAccount.getBalance().add(request.amount()));



        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        Transfer transfer = new Transfer();
        transfer.setTransactionId(UUID.randomUUID().toString());
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setAmount(request.amount());
        transfer.setStatus(TransferStatus.SUCCESS);

        Transfer saved = transferRepository.save(transfer);
        return TransferResponse.from(saved);
    }

    public TransferResponse getByTransactionId(String transactionId) {
        Transfer transfer = transferRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transactionId));
        return TransferResponse.from(transfer);
    }


}
