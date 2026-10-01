package com.financialtransaction.idempotency.repository;

import com.financialtransaction.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord,Long> {
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
