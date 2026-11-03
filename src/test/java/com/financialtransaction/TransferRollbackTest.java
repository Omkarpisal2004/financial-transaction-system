package com.financialtransaction;

import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.AccountType;
import com.financialtransaction.account.entity.Currency;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.ledger.repository.LedgerEntryRepository;
import com.financialtransaction.transfer.dto.TransferRequest;
import com.financialtransaction.transfer.entity.TransferStatus;
import com.financialtransaction.transfer.repository.TransferRepository;
import com.financialtransaction.transfer.service.TransferService;
import com.financialtransaction.user.entity.User;
import com.financialtransaction.user.entity.UserRole;
import com.financialtransaction.user.entity.UserStatus;
import com.financialtransaction.user.repository.UserRepository;

import org.springframework.dao.DataAccessException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransferRollbackTest {

    private static final String TRIGGER_NAME =
            "test_fail_credit_ledger_insert";

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserDetailsService userDetailsService;

    private Long sourceAccountId;
    private Long destinationAccountId;

    private BigDecimal sourceInitialBalance;
    private BigDecimal destinationInitialBalance;

    private long initialTransferCount;
    private long initialLedgerCount;
    private long initialFailedTransferCount;

    private String email;

    @BeforeEach
    void setUp() {

        dropFailureTrigger();

        String uniqueId = UUID.randomUUID().toString();

        email = "rollback-" + uniqueId + "@example.com";

        // Create a real database user.
        User user = new User();
        user.setName("Rollback Test User");
        user.setEmail(email);
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);

        user = userRepository.saveAndFlush(user);

        // Create source account.
        Account source = new Account();
        source.setUser(user);
        source.setAccountNumber("1" + randomDigits(11));
        source.setBalance(new BigDecimal("10000.0000"));
        source.setCurrency(Currency.INR);
        source.setStatus(AccountStatus.ACTIVE);
        source.setAccountType(AccountType.SAVINGS);

        source = accountRepository.saveAndFlush(source);

        // Create destination account.
        Account destination = new Account();
        destination.setUser(user);
        destination.setAccountNumber("2" + randomDigits(11));
        destination.setBalance(new BigDecimal("5000.0000"));
        destination.setCurrency(Currency.INR);
        destination.setStatus(AccountStatus.ACTIVE);
        destination.setAccountType(AccountType.SAVINGS);

        destination = accountRepository.saveAndFlush(destination);

        sourceAccountId = source.getId();
        destinationAccountId = destination.getId();

        sourceInitialBalance = source.getBalance();
        destinationInitialBalance = destination.getBalance();

        initialTransferCount = transferRepository.count();
        initialLedgerCount = ledgerEntryRepository.count();

        initialFailedTransferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE status = ?",
                Long.class,
                TransferStatus.FAILED.name()
        );

        // Use the application's actual UserDetails implementation.
        UserDetails userDetails =
                userDetailsService.loadUserByUsername(email);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                )
        );

        // Inject a real MySQL database failure for CREDIT ledger inserts.
        createFailureTrigger();
    }

    @AfterEach
    void tearDown() {
        dropFailureTrigger();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRollbackTransferProcessingAndMarkTransferFailed()
    {

        TransferRequest request = new TransferRequest(
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("1000.00")
        );

        String idempotencyKey = UUID.randomUUID().toString();

        // Processing should fail because the database trigger
        // rejects CREDIT ledger inserts.
        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> transferService.createTransferIdempotent(
                        idempotencyKey,
                        request
                )
        );

        assertTrue(
                exception.getMessage().contains(
                        "Injected credit ledger failure"
                )
                        || (exception.getCause() != null
                        && exception.getCause().getMessage() != null
                        && exception.getCause().getMessage().contains(
                        "Injected credit ledger failure"
                )),
                "Expected the injected CREDIT ledger database failure"
        );

        // Verify balances directly against MySQL.
        BigDecimal actualSourceBalance = getBalance(sourceAccountId);
        BigDecimal actualDestinationBalance = getBalance(destinationAccountId);

        assertEquals(
                0,
                sourceInitialBalance.compareTo(actualSourceBalance),
                "Source balance must be restored after rollback"
        );

        assertEquals(
                0,
                destinationInitialBalance.compareTo(actualDestinationBalance),
                "Destination balance must remain unchanged after rollback"
        );

        // PENDING transfer was independently committed.
        // It should now exist with FAILED status.
        assertEquals(
                initialTransferCount + 1,
                transferRepository.count(),
                "Transfer record should remain persisted"
        );

        long finalFailedTransferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE status = ?",
                Long.class,
                TransferStatus.FAILED.name()
        );

        assertEquals(
                initialFailedTransferCount + 1,
                finalFailedTransferCount,
                "Failed transfer should be persisted with FAILED status"
        );

        // Neither debit nor credit ledger entry should survive.
        assertEquals(
                initialLedgerCount,
                ledgerEntryRepository.count(),
                "Ledger entries must be rolled back"
        );
    }

    private BigDecimal getBalance(Long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                accountId
        );
    }

    private void createFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE TRIGGER test_fail_credit_ledger_insert
                BEFORE INSERT ON ledger_entries
                FOR EACH ROW
                BEGIN
                    IF NEW.entry_type = 'CREDIT' THEN
                        SIGNAL SQLSTATE '45000'
                        SET MESSAGE_TEXT = 'Injected credit ledger failure';
                    END IF;
                END
                """);
    }

    private void dropFailureTrigger() {
        jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS " + TRIGGER_NAME
        );
    }

    private String randomDigits(int length) {
        StringBuilder digits = new StringBuilder();

        for (int i = 0; i < length; i++) {
            digits.append((int) (Math.random() * 10));
        }

        return digits.toString();
    }
}

