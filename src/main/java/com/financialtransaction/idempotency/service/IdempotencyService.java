package com.financialtransaction.idempotency.service;

import com.financialtransaction.idempotency.entity.IdempotencyRecord;
import com.financialtransaction.idempotency.entity.IdempotencyStatus;
import com.financialtransaction.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord createInProgress(
            String idempotencyKey,
            String requestHash
    ) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.IN_PROGRESS);

        return repository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String idempotencyKey, String responseBody) {

        IdempotencyRecord record = getRecord(idempotencyKey);

        record.setResponseBody(responseBody);
        record.setStatus(IdempotencyStatus.COMPLETED);

        repository.save(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String idempotencyKey) {

        IdempotencyRecord record = getRecord(idempotencyKey);

        record.setStatus(IdempotencyStatus.FAILED);

        repository.save(record);
    }

    private IdempotencyRecord getRecord(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record not found: " + idempotencyKey
                ));
    }
}
