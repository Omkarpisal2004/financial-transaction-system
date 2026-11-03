package com.financialtransaction.transfer.service;

import com.financialtransaction.audit.service.AuditService;
import com.financialtransaction.idempotency.service.IdempotencyService;
import com.financialtransaction.security.userdetails.UserPrincipal;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.dto.TransferResponse;
import com.financialtransaction.transfer.repository.TransferRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TransferInitiationService transferInitiationService;

    @Mock
    private TransferProcessingService transferProcessingService;

    @Mock
    private TransferFailureService transferFailureService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserPrincipal principal;

    private TransferService transferService;

    @BeforeEach
    void setup() {

        transferService = new TransferService(
                transferRepository,
                idempotencyService,
                transferInitiationService,
                transferProcessingService,
                transferFailureService,
                new ObjectMapper(),
                auditService,
                new SimpleMeterRegistry()
        );

        when(principal.getId()).thenReturn(1L);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.emptyList()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authToken);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCompleteTransferSuccessfully() {

        String idempotencyKey = "test-key-123";

        TransferRequest request = new TransferRequest(
                1L,
                2L,
                new BigDecimal("100.00")
        );

        TransferResponse expectedResponse = new TransferResponse(
                "txn-123",
                1L,
                2L,
                new BigDecimal("100.00"),
                "SUCCESS"
        );

        when(idempotencyService.findByKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transferInitiationService.createPendingTransfer(request))
                .thenReturn("txn-123");

        when(transferProcessingService.processTransfer(
                "txn-123",
                request,
                1L
        )).thenReturn(expectedResponse);

        TransferResponse actualResponse =
                transferService.createTransferIdempotent(
                        idempotencyKey,
                        request
                );

        assertNotNull(actualResponse);
        assertEquals("txn-123", actualResponse.transactionId());
        assertEquals("SUCCESS", actualResponse.status());

        verify(idempotencyService).createInProgress(
                eq(idempotencyKey),
                anyString()
        );

        verify(transferInitiationService)
                .createPendingTransfer(request);

        verify(transferProcessingService)
                .processTransfer("txn-123", request, 1L);

        verify(idempotencyService)
                .markCompleted(eq(idempotencyKey), anyString());

        verifyNoInteractions(transferFailureService);
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldMarkTransferAndIdempotencyFailed_whenProcessingFails() {

        String idempotencyKey = "test-key-456";

        TransferRequest request = new TransferRequest(
                1L,
                2L,
                new BigDecimal("500.00")
        );

        RuntimeException exception =
                new IllegalArgumentException("Insufficient balance");

        when(idempotencyService.findByKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(transferInitiationService.createPendingTransfer(request))
                .thenReturn("txn-456");

        when(transferProcessingService.processTransfer(
                "txn-456",
                request,
                1L
        )).thenThrow(exception);

        RuntimeException actualException = assertThrows(
                RuntimeException.class,
                () -> transferService.createTransferIdempotent(
                        idempotencyKey,
                        request
                )
        );

        assertEquals(
                "Insufficient balance",
                actualException.getMessage()
        );

        verify(transferFailureService).markFailed(
                "txn-456",
                "Insufficient balance"
        );

        verify(idempotencyService).markFailed(idempotencyKey);

        verify(auditService).log(
                1L,
                "TRANSFER_FAILED",
                "txn-456",
                "FAILURE",
                "Insufficient balance"
        );

        verify(idempotencyService, never())
                .markCompleted(anyString(), anyString());
    }
}
