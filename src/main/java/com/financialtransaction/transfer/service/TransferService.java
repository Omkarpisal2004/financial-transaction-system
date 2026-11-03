package com.financialtransaction.transfer.service;

import com.financialtransaction.audit.service.AuditService;
import com.financialtransaction.idempotency.entity.IdempotencyRecord;
import com.financialtransaction.idempotency.entity.IdempotencyStatus;
import com.financialtransaction.idempotency.service.IdempotencyService;
import com.financialtransaction.idempotency.util.HashUtil;
import com.financialtransaction.security.userdetails.UserPrincipal;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.dto.TransferResponse;
import com.financialtransaction.transfer.entity.Transfer;
import com.financialtransaction.transfer.repository.TransferRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class TransferService {

    private static final Logger logger =
            LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final IdempotencyService idempotencyService;
    private final TransferInitiationService transferInitiationService;
    private final TransferProcessingService transferProcessingService;
    private final TransferFailureService transferFailureService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public TransferService(
            TransferRepository transferRepository,
            IdempotencyService idempotencyService,
            TransferInitiationService transferInitiationService,
            TransferProcessingService transferProcessingService,
            TransferFailureService transferFailureService,
            ObjectMapper objectMapper,
            AuditService auditService,
            MeterRegistry meterRegistry
    ) {
        this.transferRepository = transferRepository;
        this.idempotencyService = idempotencyService;
        this.transferInitiationService = transferInitiationService;
        this.transferProcessingService = transferProcessingService;
        this.transferFailureService = transferFailureService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
    }

    public TransferResponse createTransferIdempotent(
            String idempotencyKey,
            TransferRequest request
    ) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key is required"
            );
        }

        Long currentUserId = getCurrentUserId();

        String requestHash = calculateRequestHash(request);

        // STEP 1: Check whether this key already exists.
        var existing = idempotencyService.findByKey(idempotencyKey);

        if (existing.isPresent()) {
            return handleExistingRequest(
                    existing.get(),
                    requestHash,
                    idempotencyKey
            );
        }

        // STEP 2: Save IN_PROGRESS independently.
        try {
            idempotencyService.createInProgress(
                    idempotencyKey,
                    requestHash
            );
        } catch (DataIntegrityViolationException exception) {

            // Another concurrent request may have inserted the same key.
            var concurrentRecord =
                    idempotencyService.findByKey(idempotencyKey);

            if (concurrentRecord.isPresent()) {
                return handleExistingRequest(
                        concurrentRecord.get(),
                        requestHash,
                        idempotencyKey
                );
            }

            throw exception;
        }

        String transactionId = null;

        try {

            // STEP 3: Create PENDING transfer independently.
            transactionId =
                    transferInitiationService.createPendingTransfer(request);

            // STEP 4: Process transfer atomically.
            TransferResponse response =
                    transferProcessingService.processTransfer(
                            transactionId,
                            request,
                            currentUserId
                    );

            // STEP 5: Processing has committed successfully.
            String responseBody = objectMapper.writeValueAsString(response);

            idempotencyService.markCompleted(
                    idempotencyKey,
                    responseBody
            );

            meterRegistry.counter("transfers.success").increment();

            logger.info(
                    "Transfer completed successfully: transactionId={}",
                    transactionId
            );

            return response;

        } catch (RuntimeException exception) {

            logger.error(
                    "Transfer failed: transactionId={}, reason={}",
                    transactionId,
                    exception.getMessage(),
                    exception
            );

            meterRegistry.counter("transfers.failure").increment();

            // STEP 6: Mark existing PENDING transfer as FAILED.
            if (transactionId != null) {
                try {
                    transferFailureService.markFailed(
                            transactionId,
                            exception.getMessage()
                    );
                } catch (RuntimeException failureException) {
                    logger.error(
                            "Failed to mark transfer as FAILED: {}",
                            transactionId,
                            failureException
                    );

                    exception.addSuppressed(failureException);
                }
            }

            // STEP 7: Mark idempotency record as FAILED.
            try {
                idempotencyService.markFailed(idempotencyKey);
            } catch (RuntimeException failureException) {
                logger.error(
                        "Failed to update idempotency record",
                        failureException
                );

                exception.addSuppressed(failureException);
            }

            // Preserve existing failure audit behavior.
            try {
                auditService.log(
                        currentUserId,
                        "TRANSFER_FAILED",
                        transactionId,
                        "FAILURE",
                        exception.getMessage()
                );
            } catch (RuntimeException auditException) {
                logger.error(
                        "Failed to persist transfer failure audit",
                        auditException
                );

                exception.addSuppressed(auditException);
            }

            throw exception;
        }
    }

    private TransferResponse handleExistingRequest(
            IdempotencyRecord existing,
            String requestHash,
            String idempotencyKey
    ) {

        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IllegalArgumentException(
                    "Idempotency key already used with a different request payload"
            );
        }

        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            try {
                return objectMapper.readValue(
                        existing.getResponseBody(),
                        TransferResponse.class
                );
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Failed to deserialize stored transfer response",
                        exception
                );
            }
        }

        if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Request with this idempotency key is already being processed: "
                            + idempotencyKey
            );
        }

        throw new IllegalStateException(
                "Previous request with this idempotency key failed: "
                        + idempotencyKey
        );
    }

    private String calculateRequestHash(TransferRequest request) {

        try {
            String payload = objectMapper.writeValueAsString(request);
            return HashUtil.sha256(payload);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to serialize transfer request",
                    exception
            );
        }
    }

    private Long getCurrentUserId() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        UserPrincipal principal =
                (UserPrincipal) authentication.getPrincipal();

        return principal.getId();
    }

    public TransferResponse getByTransactionId(String transactionId) {

        Transfer transfer = transferRepository
                .findByTransactionId(transactionId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transfer not found: " + transactionId
                        ));

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        boolean isAdmin = authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

        if (!isAdmin) {

            UserPrincipal principal =
                    (UserPrincipal) authentication.getPrincipal();

            Long currentUserId = principal.getId();

            Long fromUserId =
                    transfer.getFromAccount().getUser().getId();

            Long toUserId =
                    transfer.getToAccount().getUser().getId();

            if (!currentUserId.equals(fromUserId)
                    && !currentUserId.equals(toUserId)) {

                throw new AccessDeniedException(
                        "You do not have permission to view this transfer"
                );
            }
        }

        return TransferResponse.from(transfer);
    }
}