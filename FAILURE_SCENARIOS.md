&#x20;Failure Scenarios — Testing \& Observations



This document records deliberate failure testing performed on the system, 

what was observed, and what was learned/fixed as a result.



&#x20;1. Application Exception Mid-Transfer (Phase 4)



Test: Simulated a RuntimeException after debit but before credit, without @Transactional.



Observed: Partial transfer — source account debited, destination never credited. 

Money effectively disappeared from the system (total balance across both accounts decreased).



Fix: Added @Transactional to the transfer method. Re-tested — on the same simulated 

exception, both debit and credit were rolled back; balances remained unchanged.



2\. Concurrent Requests — Lost Update (Phase 6)



Test: Two threads simultaneously transferring from the same source account 

(₹8,000 and ₹7,000 from an account with ₹10,000), using a multi-threaded JUnit test.



Observed: Both threads read the same stale balance before either committed, 

leading to both debits succeeding — resulting in a negative-trending balance calculation 

(lost update problem).



Fix: Added pessimistic write locking (`@Lock(LockModeType.PESSIMISTIC\_WRITE)`) on 

account reads during transfer. Re-tested — second thread now waits for the first 

transaction to commit, then correctly sees the updated balance and fails with 

"insufficient balance" instead of corrupting the balance.



&#x20;3. Deadlock (Phase 6)



Test: Same concurrent transfer test as above.



Observed: MySQL detected a deadlock (`Deadlock found when trying to get lock`) 

because both transactions attempted to lock the two accounts in different orders.



Fix: Implemented consistent lock ordering — always lock the account with the 

lower ID first, regardless of transfer direction. Re-tested — no deadlocks occurred, 

and transactions serialized correctly.



&#x20;4. Duplicate Requests (Phase 7)



Test: Sent the same transfer request twice with the same Idempotency-Key header.



Observed (before fix): Would have processed as two separate transfers, double-debiting the account.



Fix: Implemented idempotency key tracking with request-hash validation. 

Re-tested — duplicate requests return the original response without reprocessing; 

a different payload with the same key is rejected.



&#x20;5. Database Unavailability

Test A — App fails to start with DB down: Stopped the MySQL service, then started 

the Spring Boot application.



Observed: Application failed to boot — Hibernate could not establish a connection 

during schema validation/startup, causing a clean startup failure (not a silent hang).



Learning: The database is a hard dependency at startup. In production, this is why 

readiness probes (e.g. in Kubernetes) are used to prevent routing traffic to an 

instance that isn't actually ready.





Test B — DB goes down while app is running: Started the application normally with 

MySQL up, then stopped MySQL while the app was live, and sent a request.



Observed (before tuning): Requests hung for 10-15 seconds before failing, due to 

HikariCP's default connection retry/timeout behavior.



Fix: Set `spring.datasource.hikari.connection-timeout=5000` to fail faster 

(reduced to 5-7 seconds).

&#x20;

Remaining limitation: Even with tuning, there's still a noticeable delay before 

failure — a production system would likely add a circuit breaker (e.g. Resilience4j) 

to fail instantly after detecting repeated failures, rather than retrying every time.





&#x20;Summary

These tests collectively validate that the system fails in predictable, bounded ways 

rather than silently corrupting data or hanging indefinitely — with the database 

startup dependency and connection-timeout delay identified as the main remaining 

areas for production hardening (readiness probes, circuit breakers).

