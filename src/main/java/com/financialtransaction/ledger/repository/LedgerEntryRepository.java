package com.financialtransaction.ledger.repository;

import com.financialtransaction.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,Long> {
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId);
}
