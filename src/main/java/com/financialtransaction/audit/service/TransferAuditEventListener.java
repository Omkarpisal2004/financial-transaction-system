package com.financialtransaction.audit.service;

import com.financialtransaction.audit.event.TransferCompleteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransferAuditEventListener {

    private static final Logger logger =
            LoggerFactory.getLogger(TransferAuditEventListener.class);

    private final AuditService auditService;

    public TransferAuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransferCompleted(TransferCompleteEvent event) {

        try {
            auditService.log(
                    event.userId(),
                    "TRANSFER_COMPLETED",
                    event.transactionId(),
                    "SUCCESS",
                    event.details()
            );

        } catch (RuntimeException e) {
            logger.error(
                    "Transfer committed, but success audit could not be persisted. transactionId={}",
                    event.transactionId(),
                    e
            );
        }
    }
}