//package com.financialtransaction;
//
//import com.financialtransaction.reconciliation.entity.ReconciliationException;
//import com.financialtransaction.reconciliation.entity.Reconciliation;
//import com.financialtransaction.reconciliation.repository.ReconciliationExceptionRepository;
//import com.financialtransaction.reconciliation.repository.ReconciliationRunRepository;
//import com.financialtransaction.reconciliation.service.ReconciliationService;
//import com.financialtransaction.reconciliation.dto.ReconciliationReport;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.domain.Sort;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@SpringBootTest
//class ReconciliationPersistenceTest {
//
//
//    @Autowired
//    private ReconciliationService reconciliationService;
//
//    @Autowired
//    private ReconciliationRunRepository runRepository;
//
//    @Autowired
//    private ReconciliationExceptionRepository exceptionRepository;
//
//    @Test
//    void reconciliationRun_shouldBePersistedWithCorrectExceptionCount() {
//
//        // Arrange
//        long runsBefore = runRepository.count();
//
//        // Act
//        ReconciliationReport report =
//                reconciliationService.reconcileTransfers();
//
//        // Assert: a new run was persisted
//        List<Reconciliation> newRuns = runRepository.findAll(
//                Sort.by(Sort.Direction.DESC, "id")
//        );
//
//        assertEquals(
//                runsBefore + 1,
//                newRuns.size(),
//                "Exactly one new reconciliation run should be persisted"
//        );
//
//        // Identify the run created by this test.
//        Reconciliation testRun = newRuns.stream()
//                .filter(run -> run.getId() != null)
//                .findFirst()
//                .orElseThrow(() ->
//                        new AssertionError("New reconciliation run not found")
//                );
//
//        assertNotNull(testRun.getId());
//
//        assertEquals(
//                Reconciliation.RunStatus.COMPLETED,
//                testRun.getStatus()
//        );
//
//        assertNotNull(testRun.getStartedAt());
//        assertNotNull(testRun.getCompletedAt());
//
//        // Verify exception records for this run.
//        List<ReconciliationException> exceptions =
//                exceptionRepository.findByReconciliationRunId(testRun.getId());
//
//        assertEquals(
//                testRun.getExceptionCount(),
//                exceptions.size(),
//                "Run exception count must match persisted exception records"
//        );
//
//        for (ReconciliationException exception : exceptions) {
//            assertNotNull(exception.getId());
//            assertNotNull(exception.getExceptionType());
//            assertNotNull(exception.getDescription());
//            assertNotNull(exception.getDetectedAt());
//
//            assertEquals(
//                    ReconciliationException.ExceptionStatus.OPEN,
//                    exception.getStatus()
//            );
//        }
//
//        // Verify run statistics.
//        assertEquals(
//                report.transfersChecked(),
//                testRun.getTransfersChecked()
//        );
//
//        assertEquals(
//                report.reconciledTransfers(),
//                testRun.getReconciledCount()
//        );
//        assertEquals(
//                runsBefore + 1,
//                runRepository.count(),
//                "Exactly one new reconciliation run should be persisted"
//        );
//    }
//}
