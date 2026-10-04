&#x20;Load Test Findings — Round 2 (after fixing pool size + audit log column)



\- Increased HikariCP max pool size from 10 to 20 to match test thread count.



\- Result: throughput did NOT improve proportionally — single-row pessimistic 

&#x20; locking on Account 1 serialized all 20 concurrent threads, since each transfer 

&#x20; requires an exclusive lock on the same "hot" account row.



\- Observed `Lock wait timeout exceeded` errors under sustained contention, 

&#x20; confirming the queue depth grew beyond MySQL's lock wait timeout in some cases.



\- Conclusion: connection pool size and row-level lock contention are separate 

&#x20; bottlenecks. Increasing pool size only helps when requests touch different 

&#x20; rows; for hot-row contention, the fix must address serialization itself — 

&#x20; e.g., optimistic locking with retry/backoff, request queuing, or sharding 

&#x20; high-traffic accounts.

