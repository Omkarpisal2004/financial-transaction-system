package com.financialtransaction;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.Currency;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ConcurrencyTest {
    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    private static final Long ACCOUNT_A_ID = 1L;  // ACC001
    private static final Long ACCOUNT_B_ID = 2L;  // ACC002

    @Test
    void concurrentTransfers_shouldNotAllowNegativeBalance() throws InterruptedException {
        // NOTE: reset balances manually in DB before running this test:
        // A = 10000, B = 0

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // Thread 1: A -> B, ₹8,000
        executor.submit(() -> {
            try {
                transferService.createTransfer(new TransferRequest(ACCOUNT_A_ID, ACCOUNT_B_ID, new BigDecimal("8000")));
            } catch (Exception e) {
                System.out.println("Transfer 1 failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // Thread 2: A -> B, ₹7,000
        executor.submit(() -> {
            try {
                transferService.createTransfer(new TransferRequest(ACCOUNT_A_ID, ACCOUNT_B_ID, new BigDecimal("7000")));
            } catch (Exception e) {
                System.out.println("Transfer 2 failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executor.shutdown();

        Account finalA = accountRepository.findById(ACCOUNT_A_ID).orElseThrow();
        System.out.println("Final balance of A: " + finalA.getBalance());

        // This assertion will likely FAIL without proper locking — proving the bug
        assertTrue(finalA.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Balance should never go negative!");
    }
}
