 package com.financialtransaction.reconciliation.repository;

import com.financialtransaction.reconciliation.entity.Reconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository
        extends JpaRepository<Reconciliation, Long> {
}
