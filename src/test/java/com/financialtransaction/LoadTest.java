package com.financialtransaction;

import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class LoadTest {

    @Autowired
    private TransferService transferService;

    @BeforeEach
    void setup() {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    @Test
    void basicLoadTest_measureThroughputAndLatency() throws InterruptedException {
        int numberOfRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(numberOfRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Capture the security context from the main thread so worker threads can reuse it
        var mainContext = SecurityContextHolder.getContext();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numberOfRequests; i++) {
            executor.submit(() -> {
                SecurityContextHolder.setContext(mainContext);  // propagate auth to this worker thread
                try {
                    transferService.createTransfer(new TransferRequest(1L, 2L, new BigDecimal("1")));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("Request failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long totalTimeMs = endTime - startTime;

        System.out.println("=== LOAD TEST RESULTS ===");
        System.out.println("Total requests: " + numberOfRequests);
        System.out.println("Successful: " + successCount.get());
        System.out.println("Failed: " + failureCount.get());
        System.out.println("Total time: " + totalTimeMs + " ms");
        System.out.println("Throughput: " + (numberOfRequests * 1000.0 / totalTimeMs) + " requests/sec");
    }
}