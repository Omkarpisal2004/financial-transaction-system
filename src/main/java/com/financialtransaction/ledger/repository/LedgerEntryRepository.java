package com.financialtransaction.ledger.repository;

import com.financialtransaction.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,Long>, JpaSpecificationExecutor<LedgerEntry> {
//    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId);
}
