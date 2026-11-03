package com.financialtransaction;

import com.financialtransaction.account.entity.*;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.security.userdetails.UserPrincipal;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.service.TransferService;
import com.financialtransaction.user.entity.*;
import com.financialtransaction.user.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoadTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void basicLoadTest_measureThroughputAndLatency()
            throws InterruptedException, ExecutionException {

        int numberOfRequests = 100;
        int threadCount = 20;

        User user = createTestUser();

        Account fromAccount = createAccount(
                user, new BigDecimal("1000")
        );

        Account toAccount = createAccount(
                user, BigDecimal.ZERO
        );

        Long fromAccountId = fromAccount.getId();
        Long toAccountId = toAccount.getId();

        UserPrincipal principal = UserPrincipal.fromUser(user);

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch latch =
                new CountDownLatch(numberOfRequests);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        long startTime = System.nanoTime();

        try {
            List<Future<?>> futures = new CopyOnWriteArrayList<>();

            for (int i = 0; i < numberOfRequests; i++) {

                Future<?> future = executor.submit(() -> {

                    SecurityContext workerContext =
                            SecurityContextHolder.createEmptyContext();

                    workerContext.setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    principal.getAuthorities()
                            )
                    );

                    SecurityContextHolder.setContext(workerContext);

                    try {
                        // Each independent request gets a unique key.
                        String idempotencyKey =
                                UUID.randomUUID().toString();

                        transferService.createTransferIdempotent(
                                idempotencyKey,
                                new TransferRequest(
                                        fromAccountId,
                                        toAccountId,
                                        BigDecimal.ONE
                                )
                        );

                        successCount.incrementAndGet();

                    } catch (RuntimeException e) {
                        failureCount.incrementAndGet();
                        throw e;

                    } finally {
                        SecurityContextHolder.clearContext();
                        latch.countDown();
                    }
                });

                futures.add(future);
            }

            boolean completed =
                    latch.await(60, TimeUnit.SECONDS);

            assertTrue(
                    completed,
                    "Not all requests completed within 60 seconds"
            );

            for (Future<?> future : futures) {
                future.get();
            }

        } finally {
            executor.shutdown();

            assertTrue(
                    executor.awaitTermination(30, TimeUnit.SECONDS),
                    "Executor did not terminate within 30 seconds"
            );
        }

        long totalTimeMs =
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startTime
                );

        Account updatedFrom =
                accountRepository.findById(fromAccountId).orElseThrow();

        Account updatedTo =
                accountRepository.findById(toAccountId).orElseThrow();

        System.out.println("=== LOAD TEST RESULTS ===");
        System.out.println("Total requests: " + numberOfRequests);
        System.out.println("Successful: " + successCount.get());
        System.out.println("Failed: " + failureCount.get());
        System.out.println("Total time: " + totalTimeMs + " ms");
        System.out.println(
                "Throughput: "
                        + (numberOfRequests * 1000.0 / totalTimeMs)
                        + " requests/sec"
        );

        assertEquals(numberOfRequests, successCount.get());
        assertEquals(0, failureCount.get());

        assertEquals(
                new BigDecimal("900.0000"),
                updatedFrom.getBalance()
        );

        assertEquals(
                new BigDecimal("100.0000"),
                updatedTo.getBalance()
        );

        assertEquals(
                new BigDecimal("1000.0000"),
                updatedFrom.getBalance().add(updatedTo.getBalance())
        );
    }

    private User createTestUser() {

        String uniqueId = UUID.randomUUID().toString();

        User user = new User();
        user.setName("Load Test User");
        user.setEmail("loadtest-" + uniqueId + "@test.com");
        user.setPasswordHash("test-password");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        user.setTokenVersion(0);

        return userRepository.saveAndFlush(user);
    }

    private Account createAccount(User user, BigDecimal balance) {

        Account account = new Account();

        account.setUser(user);
        account.setAccountNumber(
                "LT" + UUID.randomUUID().toString()
                        .replace("-", "").substring(0, 10)
        );

        account.setBalance(balance);
        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.SAVINGS);

        return accountRepository.saveAndFlush(account);
    }
}
