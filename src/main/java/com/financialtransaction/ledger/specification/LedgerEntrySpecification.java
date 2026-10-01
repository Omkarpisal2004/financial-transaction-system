package com.financialtransaction.ledger.specification;

import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class LedgerEntrySpecification {

    public static Specification<LedgerEntry> hasAccountId(Long accountId) {
        return (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<LedgerEntry> hasEntryType(EntryType entryType) {
        return (root, query, cb) -> entryType == null ? null : cb.equal(root.get("entryType"), entryType);
    }

    public static Specification<LedgerEntry> createdAfter(LocalDateTime start) {
        return (root, query, cb) -> start == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), start);
    }

    public static Specification<LedgerEntry> createdBefore(LocalDateTime end) {
        return (root, query, cb) -> end == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), end);
    }

}
