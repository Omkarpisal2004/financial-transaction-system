package com.financialtransaction;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.AccountType;
import com.financialtransaction.account.entity.Currency;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.security.userdetails.UserPrincipal;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.service.TransferService;
import com.financialtransaction.user.entity.User;
import com.financialtransaction.user.entity.UserRole;
import com.financialtransaction.user.entity.UserStatus;
import com.financialtransaction.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void concurrentTransfers_shouldPreventNegativeBalance()
            throws Exception {

        // 1. Create a dedicated test user.
        User user = new User();
        user.setName("Concurrency Test User");
        user.setEmail("concurrency-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        user = userRepository.saveAndFlush(user);

        UserPrincipal principal = UserPrincipal.fromUser(user);

        // 2. Create source account with ₹10,000.
        Account source = new Account();
        source.setUser(user);
        source.setAccountNumber(generateAccountNumber());
        source.setBalance(new BigDecimal("10000.0000"));
        source.setCurrency(Currency.INR);
        source.setStatus(AccountStatus.ACTIVE);
        source.setAccountType(AccountType.SAVINGS);

        source = accountRepository.saveAndFlush(source);

        // 3. Create destination account with ₹0.
        Account destination = new Account();
        destination.setUser(user);
        destination.setAccountNumber(generateAccountNumber());
        destination.setBalance(BigDecimal.ZERO);
        destination.setCurrency(Currency.INR);
        destination.setStatus(AccountStatus.ACTIVE);
        destination.setAccountType(AccountType.SAVINGS);

        destination = accountRepository.saveAndFlush(destination);

        Long sourceId = source.getId();
        Long destinationId = destination.getId();

        long initialSourceLedgerCount =
                ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(
                        sourceId
                ).size();

        long initialDestinationLedgerCount =
                ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(
                        destinationId
                ).size();

        // 4. Prepare two concurrent transfers.
        int numberOfThreads = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfThreads);

        CountDownLatch ready = new CountDownLatch(numberOfThreads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        try {
            Future<?> transfer8000 = executor.submit(() -> {
                runTransfer(
                        principal,
                        sourceId,
                        destinationId,
                        new BigDecimal("8000"),
                        ready,
                        start,
                        successCount,
                        failureCount
                );
            });

            Future<?> transfer7000 = executor.submit(() -> {
                runTransfer(
                        principal,
                        sourceId,
                        destinationId,
                        new BigDecimal("7000"),
                        ready,
                        start,
                        successCount,
                        failureCount
                );
            });

            // Wait until both workers are ready.
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Workers did not become ready in time"
            );

            // Release both workers together.
            start.countDown();

            // Future.get() propagates unexpected worker failures.
            transfer8000.get(30, TimeUnit.SECONDS);
            transfer7000.get(30, TimeUnit.SECONDS);

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Executor did not terminate"
            );
        }

        // 5. Reload committed database state.
        Account finalSource =
                accountRepository.findById(sourceId).orElseThrow();

        Account finalDestination =
                accountRepository.findById(destinationId).orElseThrow();

        // 6. Verify the concurrency guarantees.
        assertEquals(
                1,
                successCount.get(),
                "Exactly one transfer should succeed"
        );

        assertEquals(
                1,
                failureCount.get(),
                "Exactly one transfer should fail"
        );

        assertTrue(
                finalSource.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Source account balance must never be negative"
        );

        // ₹8,000 succeeds => ₹2,000 remains.
        // ₹7,000 succeeds => ₹3,000 remains.
        boolean validFinalBalance =
                finalSource.getBalance().compareTo(
                        new BigDecimal("2000")
                ) == 0
                        || finalSource.getBalance().compareTo(
                        new BigDecimal("3000")
                ) == 0;

        assertTrue(
                validFinalBalance,
                "Source balance should be ₹2,000 or ₹3,000, but was "
                        + finalSource.getBalance()
        );

        BigDecimal totalBalance =
                finalSource.getBalance().add(
                        finalDestination.getBalance()
                );

        assertEquals(
                0,
                totalBalance.compareTo(new BigDecimal("10000")),
                "Money must be conserved between the two accounts"
        );

        long finalSourceLedgerCount =
                ledgerEntryRepository
                        .findByAccountIdOrderByCreatedAtDesc(sourceId)
                        .size();

        long finalDestinationLedgerCount =
                ledgerEntryRepository
                        .findByAccountIdOrderByCreatedAtDesc(destinationId)
                        .size();

        assertEquals(
                initialSourceLedgerCount + 1,
                finalSourceLedgerCount,
                "One successful transfer should create one source ledger entry"
        );

        assertEquals(
                initialDestinationLedgerCount + 1,
                finalDestinationLedgerCount,
                "One successful transfer should create one destination ledger entry"
        );

        System.out.println("=== CONCURRENCY TEST RESULTS ===");
        System.out.println("Successful transfers: " + successCount.get());
        System.out.println("Failed transfers: " + failureCount.get());
        System.out.println("Final source balance: " + finalSource.getBalance());
        System.out.println(
                "Final destination balance: "
                        + finalDestination.getBalance()
        );
        System.out.println("Concurrency test passed.");
    }

    private void runTransfer(
            UserPrincipal principal,
            Long sourceId,
            Long destinationId,
            BigDecimal amount,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successCount,
            AtomicInteger failureCount
    ) {
        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        var context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for concurrent start"
                );
            }

            // Every independent transfer gets its own idempotency key.
            String idempotencyKey = UUID.randomUUID().toString();

            transferService.createTransferIdempotent(
                    idempotencyKey,
                    new TransferRequest(
                            sourceId,
                            destinationId,
                            amount
                    )
            );

            successCount.incrementAndGet();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "Transfer worker interrupted", e
            );

        } catch (IllegalArgumentException | AccessDeniedException e) {
            failureCount.incrementAndGet();

            System.out.println(
                    "Expected transfer rejection for ₹"
                            + amount + ": " + e.getMessage()
            );

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String generateAccountNumber() {
        long number = 100_000_000_000L
                + java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(900_000_000_000L);

        return String.valueOf(number);
    }
}

