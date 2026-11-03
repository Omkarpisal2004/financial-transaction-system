package com.financialtransaction.transfer.service;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.entity.Transfer;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferInitiationService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public TransferInitiationService(
            AccountRepository accountRepository,
            TransferRepository transferRepository
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String createPendingTransfer(TransferRequest request) {

        Account fromAccount = accountRepository
                .findById(request.fromAccountId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Source account not found: "
                                        + request.fromAccountId()
                        )
                );

        Account toAccount = accountRepository
                .findById(request.toAccountId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Destination account not found: "
                                        + request.toAccountId()
                        )
                );

        Transfer transfer = new Transfer();

        transfer.setTransactionId(UUID.randomUUID().toString());
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setAmount(request.amount());
        transfer.setStatus(TransferStatus.PENDING);

        Transfer savedTransfer = transferRepository.saveAndFlush(transfer);

        return savedTransfer.getTransactionId();
    }
}

