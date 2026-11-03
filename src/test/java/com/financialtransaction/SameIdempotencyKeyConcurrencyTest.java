package com.financialtransaction;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.AccountType;
import com.financialtransaction.account.entity.Currency;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.idempotency.repository.IdempotencyRecordRepository;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.security.userdetails.UserPrincipal;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.dto.TransferResponse;
import com.financialtransaction.transfer.repository.TransferRepository;
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

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SameIdempotencyKeyConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentRequestsWithSameIdempotencyKey_shouldExecuteTransferOnlyOnce()
            throws Exception {

        // 1. Create dedicated test user.
        User user = new User();
        user.setName("Same Idempotency Test User");
        user.setEmail("same-idempotency-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        user = userRepository.saveAndFlush(user);

        UserPrincipal principal = UserPrincipal.fromUser(user);

        // 2. Create source account with ₹10,000.
        Account source = new Account();
        source.setUser(user);
        source.setAccountNumber(generateAccountNumber());
        source.setBalance(new BigDecimal("10000.00"));
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

        // 4. All workers use exactly the same request and key.
        String idempotencyKey = UUID.randomUUID().toString();

        TransferRequest request = new TransferRequest(
                sourceId,
                destinationId,
                new BigDecimal("1000.00")
        );

        int numberOfThreads = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfThreads);

        CountDownLatch ready = new CountDownLatch(numberOfThreads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger inProgressCount = new AtomicInteger();

        Set<String> transactionIds = ConcurrentHashMap.newKeySet();

        List<Future<WorkerResult>> futures = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        try {

            // 5. Submit 10 concurrent workers.
            for (int i = 0; i < numberOfThreads; i++) {

                futures.add(executor.submit(() -> {

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

                        try {

                            TransferResponse response =
                                    transferService.createTransferIdempotent(
                                            idempotencyKey,
                                            request
                                    );

                            return new WorkerResult(
                                    WorkerStatus.SUCCESS,
                                    Optional.of(response)
                            );

                        } catch (IllegalStateException exception) {

                            String message = exception.getMessage();

                            // Expected result when another request
                            // is already processing the same key.
                            if (message != null && message.startsWith(
                                    "Request with this idempotency key is already being processed:"
                            )) {

                                return new WorkerResult(
                                        WorkerStatus.IN_PROGRESS,
                                        Optional.empty()
                                );
                            }

                            // Unexpected exception should fail the test.
                            throw exception;
                        }

                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }

            // Wait until all workers are ready.
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Workers did not become ready in time"
            );

            // Start all requests together.
            start.countDown();

            // 6. Collect every worker result.
            for (int i = 0; i < futures.size(); i++) {

                try {

                    WorkerResult result =
                            futures.get(i).get(60, TimeUnit.SECONDS);

                    if (result.status() == WorkerStatus.SUCCESS) {

                        successCount.incrementAndGet();

                        result.response().ifPresent(response -> {
                            transactionIds.add(response.transactionId());
                        });

                    } else if (result.status() == WorkerStatus.IN_PROGRESS) {

                        inProgressCount.incrementAndGet();
                    }

                    System.out.println(
                            "Worker " + i + " result: " + result.status()
                    );

                } catch (Exception exception) {

                    failures.add(exception);

                    System.out.println(
                            "Worker " + i + " failed: " + exception
                    );
                }
            }

        } finally {

            start.countDown();

            executor.shutdown();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();

                assertTrue(
                        executor.awaitTermination(10, TimeUnit.SECONDS),
                        "Executor did not terminate"
                );
            }
        }

        // 7. Fail only after collecting all worker exceptions.
        if (!failures.isEmpty()) {

            AssertionError error = new AssertionError(
                    failures.size() + " worker(s) failed"
            );

            failures.forEach(error::addSuppressed);

            throw error;
        }

        // 8. Reload committed database state.
        Account finalSource =
                accountRepository.findById(sourceId).orElseThrow();

        Account finalDestination =
                accountRepository.findById(destinationId).orElseThrow();

        // 9. Verify all 10 requests were handled.
        assertEquals(
                numberOfThreads,
                successCount.get() + inProgressCount.get(),
                "Every request should either succeed or encounter IN_PROGRESS"
        );

        assertTrue(
                successCount.get() >= 1,
                "At least one request should return a successful response"
        );

        // All successful responses must point to the same transfer.
        assertEquals(
                1,
                transactionIds.size(),
                "All successful responses must have the same transaction ID"
        );

        // 10. Verify balances: transfer must execute exactly once.
        assertEquals(
                0,
                finalSource.getBalance().compareTo(
                        new BigDecimal("9000.00")
                ),
                "Source balance should be ₹9,000"
        );

        assertEquals(
                0,
                finalDestination.getBalance().compareTo(
                        new BigDecimal("1000.00")
                ),
                "Destination balance should be ₹1,000"
        );

        // 11. Verify exactly one ledger entry per account.
        assertEquals(
                1,
                ledgerEntryRepository
                        .findByAccountIdOrderByCreatedAtDesc(sourceId)
                        .size(),
                "Exactly one debit entry should exist"
        );

        assertEquals(
                1,
                ledgerEntryRepository
                        .findByAccountIdOrderByCreatedAtDesc(destinationId)
                        .size(),
                "Exactly one credit entry should exist"
        );

        // 12. Verify idempotency record.
        var idempotencyRecord =
                idempotencyRecordRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow();

        assertEquals(
                "COMPLETED",
                idempotencyRecord.getStatus().name(),
                "Idempotency record should be COMPLETED"
        );

        assertNotNull(
                idempotencyRecord.getResponseBody(),
                "Cached response should not be null"
        );

        // 13. Verify cached response points to the persisted transfer.
        TransferResponse cachedResponse =
                objectMapper.readValue(
                        idempotencyRecord.getResponseBody(),
                        TransferResponse.class
                );

        assertNotNull(cachedResponse.transactionId());

        assertEquals(
                cachedResponse.transactionId(),
                transactionIds.iterator().next(),
                "Cached response and successful responses should share transaction ID"
        );

        assertTrue(
                transferRepository
                        .findByTransactionId(cachedResponse.transactionId())
                        .isPresent(),
                "The transfer should exist in the database"
        );

        System.out.println("=== SAME IDEMPOTENCY KEY TEST ===");
        System.out.println("Total requests: " + numberOfThreads);
        System.out.println("Successful responses: " + successCount.get());
        System.out.println("IN_PROGRESS rejections: " + inProgressCount.get());
        System.out.println("Final source balance: " + finalSource.getBalance());
        System.out.println("Final destination balance: " + finalDestination.getBalance());
        System.out.println("Transaction ID: " + cachedResponse.transactionId());
        System.out.println("Same idempotency key test passed.");
    }

    private String generateAccountNumber() {

        long number = 100_000_000_000L
                + java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(900_000_000_000L);

        return String.valueOf(number);
    }

    private enum WorkerStatus {
        SUCCESS,
        IN_PROGRESS
    }

    private record WorkerResult(
            WorkerStatus status,
            Optional<TransferResponse> response
    ) {}
}