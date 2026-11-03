package com.financialtransaction;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.audit.repository.AuditLogRepository;
import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.reconciliation.dto.ReconciliationReport;
import com.financialtransaction.reconciliation.dto.TransferReconciliationItem;
import com.financialtransaction.reconciliation.repository.ReconciliationExceptionRepository;
import com.financialtransaction.reconciliation.repository.ReconciliationRunRepository;
import com.financialtransaction.reconciliation.service.ReconciliationService;
import com.financialtransaction.transfer.entity.Transfer;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @Mock
    private AccountRepository accountRepository;


    @Mock
    private ReconciliationRunRepository runRepository;

    @Mock
    private ReconciliationExceptionRepository exceptionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private Account sender;
    private Account receiver;
    private Transfer successfulTransfer;

    @BeforeEach
    void setUp() {
        sender = new Account();
        sender.setId(1L);

        receiver = new Account();
        receiver.setId(2L);

        successfulTransfer = new Transfer();
        successfulTransfer.setId(100L);
        successfulTransfer.setTransactionId("TX-100");
        successfulTransfer.setFromAccount(sender);
        successfulTransfer.setToAccount(receiver);
        successfulTransfer.setAmount(new BigDecimal("2000.0000"));
        successfulTransfer.setStatus(TransferStatus.SUCCESS);
    }

    @Test
    void successfulTransferWithCorrectLedgerEntries_shouldReconcile() {
        LedgerEntry debit = entry(
                successfulTransfer, sender, EntryType.DEBIT, "2000.0000");
        LedgerEntry credit = entry(
                successfulTransfer, receiver, EntryType.CREDIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit, credit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.totalSuccessfulTransfers());
        assertEquals(1, report.reconciledTransfers());
        assertEquals(0, report.inconsistentTransfers());
        assertTrue(report.discrepancies().isEmpty());
    }

    @Test
    void successfulTransferWithMissingLedgerEntries_shouldReportException() {
        mockRepositories(List.of(successfulTransfer), List.of());

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.totalSuccessfulTransfers());
        assertEquals(0, report.reconciledTransfers());
        assertEquals(1, report.inconsistentTransfers());

        TransferReconciliationItem item = report.discrepancies().getFirst();

        assertEquals(100L, item.transferId());
        assertFalse(item.reconciled());
        assertTrue(item.discrepancies().contains(
                "EXPECTED_2_LEDGER_ENTRIES_BUT_FOUND_0"));
    }

    @Test
    void duplicateDebit_shouldReportException() {
        LedgerEntry debit1 = entry(
                successfulTransfer, sender, EntryType.DEBIT, "2000.0000");
        LedgerEntry debit2 = entry(
                successfulTransfer, sender, EntryType.DEBIT, "2000.0000");
        LedgerEntry credit = entry(
                successfulTransfer, receiver, EntryType.CREDIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit1, debit2, credit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());

        List<String> issues = report.discrepancies().getFirst().discrepancies();

        assertTrue(issues.contains("EXPECTED_2_LEDGER_ENTRIES_BUT_FOUND_3"));
        assertTrue(issues.contains("EXPECTED_1_DEBIT_BUT_FOUND_2"));
    }

    @Test
    void incorrectDebitAmount_shouldReportException() {
        LedgerEntry debit = entry(
                successfulTransfer, sender, EntryType.DEBIT, "1500.0000");
        LedgerEntry credit = entry(
                successfulTransfer, receiver, EntryType.CREDIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit, credit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());
        assertTrue(report.discrepancies().getFirst()
                .discrepancies().contains("DEBIT_AMOUNT_MISMATCH"));
    }

    @Test
    void incorrectDebitAccount_shouldReportException() {
        Account wrongAccount = new Account();
        wrongAccount.setId(3L);

        LedgerEntry debit = entry(
                successfulTransfer, wrongAccount, EntryType.DEBIT, "2000.0000");
        LedgerEntry credit = entry(
                successfulTransfer, receiver, EntryType.CREDIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit, credit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());
        assertTrue(report.discrepancies().getFirst()
                .discrepancies().contains("DEBIT_ACCOUNT_MISMATCH"));
    }

    @Test
    void failedTransferWithLedgerEntries_shouldReportException() {
        successfulTransfer.setStatus(TransferStatus.FAILED);

        LedgerEntry debit = entry(
                successfulTransfer, sender, EntryType.DEBIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());
        assertTrue(report.discrepancies().getFirst()
                .discrepancies().contains(
                        "FAILED_TRANSFER_HAS_LEDGER_ENTRIES"));
    }

    @Test
    void ledgerEntryReferencingUnknownTransfer_shouldReportException() {
        Transfer unknownTransfer = new Transfer();
        unknownTransfer.setId(999L);

        LedgerEntry orphan = entry(
                unknownTransfer, sender, EntryType.DEBIT, "500.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(orphan)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(2, report.inconsistentTransfers());

        assertTrue(report.discrepancies().stream()
                .anyMatch(item ->
                        item.transferId().equals(999L)
                                && item.discrepancies().contains(
                                "LEDGER_ENTRIES_REFERENCE_UNKNOWN_TRANSFER")));
    }

    private LedgerEntry entry(
            Transfer transfer,
            Account account,
            EntryType type,
            String amount
    ) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransfer(transfer);
        entry.setAccount(account);
        entry.setEntryType(type);
        entry.setAmount(new BigDecimal(amount));
        return entry;
    }

    private void mockRepositories(
            List<Transfer> transfers,
            List<LedgerEntry> entries
    ) {
        when(transferRepository.findAll()).thenReturn(transfers);
        when(ledgerEntryRepository.findAll()).thenReturn(entries);
    }

    @Test
    void missingDebit_shouldReportException() {
        LedgerEntry credit = entry(
                successfulTransfer, receiver, EntryType.CREDIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(credit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());

        List<String> issues =
                report.discrepancies().getFirst().discrepancies();

        assertTrue(issues.contains("EXPECTED_2_LEDGER_ENTRIES_BUT_FOUND_1"));
        assertTrue(issues.contains("EXPECTED_1_DEBIT_BUT_FOUND_0"));
    }

    @Test
    void missingCredit_shouldReportException() {
        LedgerEntry debit = entry(
                successfulTransfer, sender, EntryType.DEBIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());

        List<String> issues =
                report.discrepancies().getFirst().discrepancies();

        assertTrue(issues.contains("EXPECTED_2_LEDGER_ENTRIES_BUT_FOUND_1"));
        assertTrue(issues.contains("EXPECTED_1_CREDIT_BUT_FOUND_0"));
    }

    @Test
    void incorrectCreditAmount_shouldReportException() {
        LedgerEntry debit = entry(
                successfulTransfer, sender, EntryType.DEBIT, "2000.0000");

        LedgerEntry credit = entry(
                successfulTransfer, receiver, EntryType.CREDIT, "1500.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit, credit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());

        assertTrue(report.discrepancies().getFirst()
                .discrepancies().contains("CREDIT_AMOUNT_MISMATCH"));
    }

    @Test
    void incorrectCreditAccount_shouldReportException() {
        Account wrongAccount = new Account();
        wrongAccount.setId(3L);

        LedgerEntry debit = entry(
                successfulTransfer, sender, EntryType.DEBIT, "2000.0000");

        LedgerEntry credit = entry(
                successfulTransfer, wrongAccount, EntryType.CREDIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit, credit)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());

        assertTrue(report.discrepancies().getFirst()
                .discrepancies().contains("CREDIT_ACCOUNT_MISMATCH"));
    }
    @Test
    void duplicateCredit_shouldReportException() {
        LedgerEntry debit = entry(
                successfulTransfer, sender, EntryType.DEBIT, "2000.0000");

        LedgerEntry credit1 = entry(
                successfulTransfer, receiver, EntryType.CREDIT, "2000.0000");

        LedgerEntry credit2 = entry(
                successfulTransfer, receiver, EntryType.CREDIT, "2000.0000");

        mockRepositories(
                List.of(successfulTransfer),
                List.of(debit, credit1, credit2)
        );

        ReconciliationReport report =
                reconciliationService.reconcileTransfers();

        assertEquals(1, report.inconsistentTransfers());

        List<String> issues =
                report.discrepancies().getFirst().discrepancies();

        assertTrue(issues.contains("EXPECTED_2_LEDGER_ENTRIES_BUT_FOUND_3"));
        assertTrue(issues.contains("EXPECTED_1_CREDIT_BUT_FOUND_2"));
    }
}

