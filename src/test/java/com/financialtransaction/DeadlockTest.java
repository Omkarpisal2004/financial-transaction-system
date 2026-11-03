package com.financialtransaction;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.AccountType;
import com.financialtransaction.account.entity.Currency;
import com.financialtransaction.account.repository.AccountRepository;
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
class DeadlockTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void oppositeDirectionTransfers_shouldNotDeadlock() throws Exception {

        // 1. Create an isolated test user.
        User user = new User();
        user.setName("Deadlock Test User");
        user.setEmail("deadlock-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        user = userRepository.saveAndFlush(user);

        UserPrincipal principal = UserPrincipal.fromUser(user);

        // 2. Create two accounts with equal balances.
        Account accountA = createAccount(
                user,
                new BigDecimal("10000")
        );

        Account accountB = createAccount(
                user,
                new BigDecimal("10000")
        );

        accountA = accountRepository.saveAndFlush(accountA);
        accountB = accountRepository.saveAndFlush(accountB);

        Long accountAId = accountA.getId();
        Long accountBId = accountB.getId();

        assertNotEquals(accountAId, accountBId);

        BigDecimal initialTotal = accountA.getBalance()
                .add(accountB.getBalance());

        // 3. Prepare simultaneous opposite-direction transfers.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        try {
            Future<?> transferAtoB = executor.submit(() ->
                    executeTransfer(
                            principal,
                            accountAId,
                            accountBId,
                            new BigDecimal("1000"),
                            ready,
                            start,
                            successCount,
                            failureCount
                    )
            );

            Future<?> transferBtoA = executor.submit(() ->
                    executeTransfer(
                            principal,
                            accountBId,
                            accountAId,
                            new BigDecimal("2000"),
                            ready,
                            start,
                            successCount,
                            failureCount
                    )
            );

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Workers failed to become ready"
            );

            // Release both threads together.
            start.countDown();

            // Fail the test if either operation hangs.
            transferAtoB.get(20, TimeUnit.SECONDS);
            transferBtoA.get(20, TimeUnit.SECONDS);

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Executor did not terminate"
            );
        }

        // 4. Verify the final database state.
        Account finalA =
                accountRepository.findById(accountAId).orElseThrow();

        Account finalB =
                accountRepository.findById(accountBId).orElseThrow();

        BigDecimal finalTotal = finalA.getBalance()
                .add(finalB.getBalance());

        assertEquals(
                2,
                successCount.get(),
                "Both transfers should succeed"
        );

        assertEquals(
                0,
                failureCount.get(),
                "Neither transfer should fail"
        );

        assertTrue(
                finalA.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Account A must not have a negative balance"
        );

        assertTrue(
                finalB.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Account B must not have a negative balance"
        );

        assertEquals(
                0,
                initialTotal.compareTo(finalTotal),
                "Total money must remain unchanged"
        );

        assertEquals(
                0,
                finalA.getBalance().compareTo(new BigDecimal("11000")),
                "Account A should finish with ₹11,000"
        );

        assertEquals(
                0,
                finalB.getBalance().compareTo(new BigDecimal("9000")),
                "Account B should finish with ₹9,000"
        );

        System.out.println("=== DEADLOCK TEST RESULTS ===");
        System.out.println("Successful transfers: " + successCount.get());
        System.out.println("Failed transfers: " + failureCount.get());
        System.out.println("Final Account A balance: " + finalA.getBalance());
        System.out.println("Final Account B balance: " + finalB.getBalance());
        System.out.println("Deadlock prevention test passed.");
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

    private void executeTransfer(
            UserPrincipal principal,
            Long sourceId,
            Long destinationId,
            BigDecimal amount,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successCount,
            AtomicInteger failureCount
    ) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for concurrent start"
                );
            }

            // Each independent transfer gets a unique idempotency key.
            String idempotencyKey = UUID.randomUUID().toString();

            transferService.createTransferIdempotent(
                    idempotencyKey,
                    new TransferRequest(sourceId, destinationId, amount)
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
                    "Expected transfer rejection: "
                            + sourceId + " -> " + destinationId
                            + ": " + e.getMessage()
            );

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String generateAccountNumber() {
        long number = Math.abs(UUID.randomUUID().getMostSignificantBits())
                % 900_000_000_000L + 100_000_000_000L;

        return String.valueOf(number);
    }
}