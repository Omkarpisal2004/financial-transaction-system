package com.financialtransaction.transfer.service;

import com.financialtransaction.transfer.entity.Transfer;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferFailureService {

    private final TransferRepository transferRepository;

    public TransferFailureService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String transactionId, String failureReason) {

        Transfer transfer = transferRepository
                .findByTransactionId(transactionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transfer not found: " + transactionId
                        )
                );

        // Never overwrite a successfully completed transfer.
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING transfers can be marked FAILED"
            );
        }

        transfer.setStatus(TransferStatus.FAILED);

        // Database column supports maximum 255 characters.
        if (failureReason != null && failureReason.length() > 255) {
            failureReason = failureReason.substring(0, 255);
        }

        transfer.setFailureReason(failureReason);

        transferRepository.save(transfer);
    }
}

