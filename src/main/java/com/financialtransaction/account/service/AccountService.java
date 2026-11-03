package com.financialtransaction.account.service;

import com.financialtransaction.account.dto.AccountCreateRequest;
import com.financialtransaction.account.dto.AccountResponse;
import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.security.userdetails.UserPrincipal;
import com.financialtransaction.user.repository.UserRepository;


import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountCreationAttemptService accountCreationAttemptService;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            AccountNumberGenerator accountNumberGenerator, AccountCreationAttemptService accountCreationAttemptService
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountCreationAttemptService = accountCreationAttemptService;
    }

    private static final int MAX_CREATION_ATTEMPTS = 10;

    public AccountResponse createAccount(AccountCreateRequest request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {

            throw new AccessDeniedException(
                    "Authenticated user is required to create an account"
            );
        }

        Long currentUserId = principal.getId();

        for (int attempt = 1; attempt <= MAX_CREATION_ATTEMPTS; attempt++) {

            try {

                return accountCreationAttemptService.createAccount(
                        currentUserId,
                        request.accountType()
                );

            } catch (DataIntegrityViolationException ex) {

                if (!isAccountNumberCollision(ex)) {
                    throw ex;
                }

                if (attempt == MAX_CREATION_ATTEMPTS) {
                    throw new IllegalStateException(
                            "Unable to generate a unique account number after "
                                    + MAX_CREATION_ATTEMPTS + " attempts",
                            ex
                    );
                }
            }
        }

        throw new IllegalStateException("Account creation failed");
    }


    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + id
                        ));

        verifyOwnership(account);

        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long id) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + id
                        ));

        verifyOwnership(account);

        return account.getBalance();
    }

    private void verifyOwnership(Account account) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }

        boolean isAdmin = authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

        if (isAdmin) {
            return;
        }

        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException(
                    "Invalid authenticated principal"
            );
        }

        Long currentUserId = principal.getId();

        if (!account.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException(
                    "You do not have permission to access this account"
            );
        }
    }

    @Transactional(readOnly = true)
    public void verifyAccountAccess(Long accountId) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + accountId
                        ));

        verifyOwnership(account);
    }

    private boolean isAccountNumberCollision(Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof SQLException ex) {

                String message = ex.getMessage();

                if (ex.getErrorCode() == 1062
                        && message != null
                        && message.contains("uk_account_account_number")) {

                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }
}