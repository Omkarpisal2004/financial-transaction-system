package com.financialtransaction.reconciliation.repository;

import com.financialtransaction.reconciliation.entity.ReconciliationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationExceptionRepository
        extends JpaRepository<ReconciliationException, Long> {

    List<ReconciliationException> findByReconciliationRunId(Long runId);

    List<ReconciliationException> findByStatus(
            ReconciliationException.ExceptionStatus status
    );
}

