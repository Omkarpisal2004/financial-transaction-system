package com.financialtransaction.reconciliation.service;

import com.financialtransaction.audit.entity.AuditLog;
import com.financialtransaction.audit.repository.AuditLogRepository;
import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.reconciliation.dto.ReconciliationReport;
import com.financialtransaction.reconciliation.dto.TransferReconciliationItem;
import com.financialtransaction.reconciliation.entity.ReconciliationException;
import com.financialtransaction.reconciliation.entity.Reconciliation;
import com.financialtransaction.reconciliation.repository.ReconciliationExceptionRepository;
import com.financialtransaction.reconciliation.repository.ReconciliationRunRepository;
import com.financialtransaction.transfer.entity.Transfer;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.reconciliation.dto.AccountReconciliationItem;

import java.math.BigDecimal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationExceptionRepository exceptionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccountRepository accountRepository;

    public ReconciliationService(
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ReconciliationRunRepository runRepository,
            ReconciliationExceptionRepository exceptionRepository,
            AuditLogRepository auditLogRepository, AccountRepository accountRepository
    ) {
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.runRepository = runRepository;
        this.exceptionRepository = exceptionRepository;
        this.auditLogRepository = auditLogRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public ReconciliationReport reconcileTransfers() {

        Instant checkedAt = Instant.now();

        Reconciliation run = new Reconciliation();
        run.setStartedAt(LocalDateTime.now());
        run.setStatus(Reconciliation.RunStatus.RUNNING);
        runRepository.saveAndFlush(run);

        try {
            List<Transfer> transfers = transferRepository.findAll();
            List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findAll();

            Map<Long, Transfer> transfersById = transfers.stream()
                    .collect(Collectors.toMap(
                            Transfer::getId,
                            Function.identity()
                    ));

            Map<Long, List<LedgerEntry>> entriesByTransferId =
                    ledgerEntries.stream()
                            .filter(entry -> entry.getTransfer() != null)
                            .filter(entry -> entry.getTransfer().getId() != null)
                            .collect(Collectors.groupingBy(
                                    entry -> entry.getTransfer().getId()
                            ));

            List<TransferReconciliationItem> discrepancies =
                    new ArrayList<>();

            int reconciledCount = 0;

            for (Transfer transfer : transfers) {

                List<LedgerEntry> entries = entriesByTransferId.getOrDefault(
                        transfer.getId(),
                        List.of()
                );

                List<String> issues = new ArrayList<>();

                if (transfer.getStatus() == TransferStatus.SUCCESS) {
                    validateSuccessfulTransfer(transfer, entries, issues);

                } else if (transfer.getStatus() == TransferStatus.FAILED) {
                    if (!entries.isEmpty()) {
                        issues.add("FAILED_TRANSFER_HAS_LEDGER_ENTRIES");
                    }

                } else if (!entries.isEmpty()) {
                    issues.add("PENDING_TRANSFER_HAS_LEDGER_ENTRIES");
                }

                if (issues.isEmpty()) {
                    reconciledCount++;
                } else {
                    discrepancies.add(toItem(transfer, entries, issues));
                }
            }

            // Detect ledger entries referencing nonexistent transfers.
            for (Map.Entry<Long, List<LedgerEntry>> orphan
                    : entriesByTransferId.entrySet()) {

                if (transfersById.containsKey(orphan.getKey())) {
                    continue;
                }

                List<LedgerEntry> entries = orphan.getValue();

                discrepancies.add(new TransferReconciliationItem(
                        orphan.getKey(),
                        null,
                        "UNKNOWN",
                        null,
                        entries.size(),
                        countEntries(entries, EntryType.DEBIT),
                        countEntries(entries, EntryType.CREDIT),
                        false,
                        List.of("LEDGER_ENTRIES_REFERENCE_UNKNOWN_TRANSFER")
                ));
            }


            List<AccountReconciliationItem> accountDiscrepancies =
                    new ArrayList<>();

            int accountsReconciled = 0;

            List<Account> accounts = accountRepository.findAll();

            for (Account account : accounts) {

                BigDecimal actualBalance = account.getBalance();

                BigDecimal ledgerBalance =
                        ledgerEntryRepository.calculateLedgerBalance(
                                account.getId(),
                                EntryType.CREDIT,
                                EntryType.DEBIT
                        );

                List<String> issues = new ArrayList<>();

                if (actualBalance == null) {
                    issues.add("ACCOUNT_BALANCE_IS_NULL");
                } else if (actualBalance.compareTo(ledgerBalance) != 0) {
                    issues.add("ACCOUNT_BALANCE_MISMATCH");
                }

                boolean reconciled = issues.isEmpty();

                if (reconciled) {
                    accountsReconciled++;
                } else {
                    accountDiscrepancies.add(
                            new AccountReconciliationItem(
                                    account.getId(),
                                    actualBalance,
                                    ledgerBalance,
                                    false,
                                    List.copyOf(issues)
                            )
                    );

                    ReconciliationException exception =
                            new ReconciliationException();

                    exception.setReconciliationRun(run);
                    exception.setTransferId(null);
                    exception.setTransactionId(null);
                    exception.setExceptionType("ACCOUNT_BALANCE_MISMATCH");
                    exception.setDescription(
                            "Account balance mismatch: accountId=" + account.getId()
                                    + ", actualBalance=" + actualBalance
                                    + ", ledgerBalance=" + ledgerBalance
                    );
                    exception.setStatus(
                            ReconciliationException.ExceptionStatus.OPEN
                    );

                    exceptionRepository.save(exception);
                }
            }



            // Persist each detected discrepancy.
            for (TransferReconciliationItem item : discrepancies) {
                for (String issue : item.discrepancies()) {

                    ReconciliationException exception =
                            new ReconciliationException();

                    exception.setReconciliationRun(run);
                    exception.setTransferId(item.transferId());
                    exception.setTransactionId(item.transactionId());
                    exception.setExceptionType(issue);
                    exception.setDescription(
                            "Reconciliation issue: " + issue
                                    + "; transferId=" + item.transferId()
                                    + "; status=" + item.status()
                                    + "; expectedAmount=" + item.expectedAmount()
                                    + "; ledgerEntryCount="
                                    + item.actualLedgerEntryCount()
                    );
                    exception.setStatus(
                            ReconciliationException.ExceptionStatus.OPEN
                    );

                    exceptionRepository.save(exception);
                }
            }

            run.setCompletedAt(LocalDateTime.now());
            run.setTransfersChecked(transfers.size());
            run.setReconciledCount(reconciledCount);
            int totalExceptionRecords = discrepancies.stream()
                    .mapToInt(item -> item.discrepancies().size())
                    .sum();

            totalExceptionRecords += accountDiscrepancies.stream()
                    .mapToInt(item -> item.discrepancies().size())
                    .sum();

            run.setExceptionCount(totalExceptionRecords);
            run.setStatus(Reconciliation.RunStatus.COMPLETED);

            runRepository.save(run);

            AuditLog auditLog = new AuditLog();
            auditLog.setAction("RECONCILIATION_RUN");
            auditLog.setResult("SUCCESS");
            auditLog.setDetails(
                    "runId=" + run.getId()
                            + ", transfersChecked=" + transfers.size()
                            + ", reconciledCount=" + reconciledCount
                            + ", discrepancyCount=" + discrepancies.size()
            );
            auditLogRepository.save(auditLog);

            return new ReconciliationReport(
                    checkedAt,
                    transfers.size(),
                    reconciledCount,
                    discrepancies.size(),
                    List.copyOf(discrepancies),
                    accounts.size(),
                    accountsReconciled,
                    accountDiscrepancies.size(),
                    List.copyOf(accountDiscrepancies)
            );
        } catch (RuntimeException exception) {
            // Mark the run as failed, then propagate the original failure.
            // Because this method is transactional, a thrown exception rolls
            // back the run and its exception records as well.
            run.setCompletedAt(LocalDateTime.now());
            run.setStatus(Reconciliation.RunStatus.FAILED);
            runRepository.save(run);
            throw exception;
        }
    }

    private void validateSuccessfulTransfer(
            Transfer transfer,
            List<LedgerEntry> entries,
            List<String> issues
    ) {
        if (transfer.getAmount() == null
                || transfer.getAmount().signum() <= 0) {
            issues.add("INVALID_TRANSFER_AMOUNT");
        }

        if (transfer.getFromAccount() == null
                || transfer.getToAccount() == null) {
            issues.add("TRANSFER_ACCOUNT_MISSING");
        } else if (Objects.equals(
                transfer.getFromAccount().getId(),
                transfer.getToAccount().getId())) {
            issues.add("SAME_SOURCE_AND_DESTINATION_ACCOUNT");
        }

        List<LedgerEntry> debits = entries.stream()
                .filter(entry -> entry.getEntryType() == EntryType.DEBIT)
                .toList();

        List<LedgerEntry> credits = entries.stream()
                .filter(entry -> entry.getEntryType() == EntryType.CREDIT)
                .toList();

        if (entries.size() != 2) {
            issues.add("EXPECTED_2_LEDGER_ENTRIES_BUT_FOUND_"
                    + entries.size());
        }

        if (debits.size() != 1) {
            issues.add("EXPECTED_1_DEBIT_BUT_FOUND_" + debits.size());
        }

        if (credits.size() != 1) {
            issues.add("EXPECTED_1_CREDIT_BUT_FOUND_" + credits.size());
        }

        for (LedgerEntry debit : debits) {
            if (transfer.getAmount() == null
                    || debit.getAmount() == null
                    || debit.getAmount().compareTo(transfer.getAmount()) != 0) {
                issues.add("DEBIT_AMOUNT_MISMATCH");
            }

            if (debit.getAccount() == null
                    || transfer.getFromAccount() == null
                    || !Objects.equals(
                    debit.getAccount().getId(),
                    transfer.getFromAccount().getId())) {
                issues.add("DEBIT_ACCOUNT_MISMATCH");
            }
        }

        for (LedgerEntry credit : credits) {
            if (transfer.getAmount() == null
                    || credit.getAmount() == null
                    || credit.getAmount().compareTo(transfer.getAmount()) != 0) {
                issues.add("CREDIT_AMOUNT_MISMATCH");
            }

            if (credit.getAccount() == null
                    || transfer.getToAccount() == null
                    || !Objects.equals(
                    credit.getAccount().getId(),
                    transfer.getToAccount().getId())) {
                issues.add("CREDIT_ACCOUNT_MISMATCH");
            }
        }
    }

    private TransferReconciliationItem toItem(
            Transfer transfer,
            List<LedgerEntry> entries,
            List<String> issues
    ) {
        return new TransferReconciliationItem(
                transfer.getId(),
                transfer.getTransactionId(),
                transfer.getStatus().name(),
                transfer.getAmount(),
                entries.size(),
                countEntries(entries, EntryType.DEBIT),
                countEntries(entries, EntryType.CREDIT),
                false,
                List.copyOf(issues)
        );
    }

    private int countEntries(List<LedgerEntry> entries, EntryType type) {
        return (int) entries.stream()
                .filter(entry -> entry.getEntryType() == type)
                .count();
    }
}
