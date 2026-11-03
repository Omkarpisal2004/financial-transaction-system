package com.financialtransaction.ledger.repository;

import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,Long>, JpaSpecificationExecutor<LedgerEntry> {
    @EntityGraph(attributePaths = {"transfer", "account"})
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query("""
            SELECT COALESCE(
                SUM(
                    CASE
                        WHEN le.entryType = :credit THEN le.amount
                        WHEN le.entryType = :debit THEN -le.amount
                        ELSE 0
                    END
                ),
                0
            )
            FROM LedgerEntry le
            WHERE le.account.id = :accountId
            """)
    BigDecimal calculateLedgerBalance(
            @Param("accountId") Long accountId,
            @Param("credit") EntryType credit,
            @Param("debit") EntryType debit
    );

    @EntityGraph(attributePaths = {"transfer", "account"})
    List<LedgerEntry> findByTransferId(Long transferId);
}
