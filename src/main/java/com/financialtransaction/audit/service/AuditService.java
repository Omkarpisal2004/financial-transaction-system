package com.financialtransaction.audit.service;

import com.financialtransaction.audit.entity.AuditLog;
import com.financialtransaction.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            Long userId,
            String action,
            String transactionId,
            String result,
            String details) {

        AuditLog auditLog = new AuditLog();

        auditLog.setUserId(userId);
        auditLog.setAction(action);
        auditLog.setTransactionId(transactionId);
        auditLog.setResult(result);
        auditLog.setDetails(details);


        auditLogRepository.saveAndFlush(auditLog);
    }
}