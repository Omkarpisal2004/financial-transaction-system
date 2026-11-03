package com.financialtransaction;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.AccountType;
import com.financialtransaction.account.entity.Currency;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.ledger.entity.EntryType;
import com.financialtransaction.ledger.entity.LedgerEntry;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.security.userdetails.UserPrincipal;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.dto.TransferResponse;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.service.TransferService;
import com.financialtransaction.user.entity.User;
import com.financialtransaction.user.entity.UserRole;
import com.financialtransaction.user.entity.UserStatus;
import com.financialtransaction.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LedgerConsistencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void successfulTransfer_shouldCreateConsistentDoubleEntryLedger() {

        // 1. Create a dedicated test user.
        User user = new User();
        user.setName("Ledger Test User");
        user.setEmail("ledger-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        user = userRepository.saveAndFlush(user);

        UserPrincipal principal = UserPrincipal.fromUser(user);

        // 2. Create source account with ₹10,000.
        Account source = createAccount(
                user,
                new BigDecimal("10000.00")
        );

        source = accountRepository.saveAndFlush(source);

        // 3. Create destination account with ₹5,000.
        Account destination = createAccount(
                user,
                new BigDecimal("5000.00")
        );

        destination = accountRepository.saveAndFlush(destination);

        Long sourceId = source.getId();
        Long destinationId = destination.getId();

        BigDecimal transferAmount = new BigDecimal("1000.00");

        // 4. Set authenticated user for this test thread.
        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        TransferResponse transferResponse;

        try {
            // 5. Execute transfer using idempotency.
            String idempotencyKey = UUID.randomUUID().toString();

            transferResponse =
                    transferService.createTransferIdempotent(
                            idempotencyKey,
                            new TransferRequest(
                                    sourceId,
                                    destinationId,
                                    transferAmount
                            )
                    );

        } finally {
            SecurityContextHolder.clearContext();
        }

        // 6. Verify transfer response.
        assertNotNull(transferResponse);
        assertNotNull(transferResponse.transactionId());

        assertEquals("SUCCESS", transferResponse.status());
        assertEquals(sourceId, transferResponse.fromAccountId());
        assertEquals(destinationId, transferResponse.toAccountId());

        assertEquals(
                0,
                transferResponse.amount().compareTo(transferAmount),
                "Transfer response amount must match requested amount"
        );

        // 7. Verify persisted account balances.
        Account updatedSource =
                accountRepository.findById(sourceId).orElseThrow();

        Account updatedDestination =
                accountRepository.findById(destinationId).orElseThrow();

        assertEquals(
                0,
                updatedSource.getBalance().compareTo(
                        new BigDecimal("9000.00")
                ),
                "Source balance should decrease by ₹1,000"
        );

        assertEquals(
                0,
                updatedDestination.getBalance().compareTo(
                        new BigDecimal("6000.00")
                ),
                "Destination balance should increase by ₹1,000"
        );

        // 8. Find the persisted transfer's ledger entries.
        // TransferResponse.transactionId is the transaction UUID.
        // Repository method expects the database transfer ID.
        // Resolve entries through their account history first.
        List<LedgerEntry> sourceEntries =
                ledgerEntryRepository
                        .findByAccountIdOrderByCreatedAtDesc(sourceId);

        List<LedgerEntry> destinationEntries =
                ledgerEntryRepository
                        .findByAccountIdOrderByCreatedAtDesc(destinationId);

        List<LedgerEntry> debitEntries = sourceEntries.stream()
                .filter(entry ->
                        entry.getTransfer() != null
                                && transferResponse.transactionId()
                                .equals(entry.getTransfer().getTransactionId())
                )
                .toList();

        List<LedgerEntry> creditEntries = destinationEntries.stream()
                .filter(entry ->
                        entry.getTransfer() != null
                                && transferResponse.transactionId()
                                .equals(entry.getTransfer().getTransactionId())
                )
                .toList();

        // 9. Verify exactly one DEBIT and one CREDIT.
        assertEquals(
                1,
                debitEntries.size(),
                "Transfer must have exactly one DEBIT entry"
        );

        assertEquals(
                1,
                creditEntries.size(),
                "Transfer must have exactly one CREDIT entry"
        );

        LedgerEntry debit = debitEntries.get(0);
        LedgerEntry credit = creditEntries.get(0);

        // 10. Verify entry types.
        assertEquals(EntryType.DEBIT, debit.getEntryType());
        assertEquals(EntryType.CREDIT, credit.getEntryType());

        // 11. Verify correct account references.
        assertEquals(sourceId, debit.getAccount().getId());
        assertEquals(destinationId, credit.getAccount().getId());

        // 12. Verify ledger amounts.
        assertEquals(
                0,
                debit.getAmount().compareTo(transferAmount),
                "Debit amount must match transfer amount"
        );

        assertEquals(
                0,
                credit.getAmount().compareTo(transferAmount),
                "Credit amount must match transfer amount"
        );

        // 13. Verify currency consistency.
        assertEquals(Currency.INR, debit.getAccount().getCurrency());
        assertEquals(Currency.INR, credit.getAccount().getCurrency());

        // 14. Verify both entries reference the same transfer.
        assertNotNull(debit.getTransfer());
        assertNotNull(credit.getTransfer());

        assertEquals(
                debit.getTransfer().getId(),
                credit.getTransfer().getId(),
                "Debit and credit must reference the same transfer"
        );

        assertEquals(
                transferResponse.transactionId(),
                debit.getTransfer().getTransactionId()
        );

        // 15. Verify persisted transfer amount and status.
        assertEquals(
                0,
                debit.getTransfer().getAmount().compareTo(transferAmount)
        );

        assertEquals(
                TransferStatus.SUCCESS,
                debit.getTransfer().getStatus()
        );

        System.out.println("=== LEDGER CONSISTENCY TEST RESULTS ===");
        System.out.println("Transaction ID: " + transferResponse.transactionId());
        System.out.println("Transfer amount: ₹" + transferAmount);
        System.out.println("Debit entry: ₹" + debit.getAmount());
        System.out.println("Credit entry: ₹" + credit.getAmount());
        System.out.println("Final source balance: " + updatedSource.getBalance());
        System.out.println("Final destination balance: " + updatedDestination.getBalance());
        System.out.println("Double-entry ledger consistency verified.");
    }

    private Account createAccount(User user, BigDecimal balance) {

        Account account = new Account();

        account.setUser(user);
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(balance);
        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.SAVINGS);

        return account;
    }

    private String generateAccountNumber() {

        long number =
                Math.abs(UUID.randomUUID().getMostSignificantBits())
                        % 900_000_000_000L
                        + 100_000_000_000L;

        return String.valueOf(number);
    }
}