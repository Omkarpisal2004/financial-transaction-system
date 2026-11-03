package com.financialtransaction.account.service;

import com.financialtransaction.account.dto.AccountResponse;
import com.financialtransaction.account.entity.Account;
import com.financialtransaction.account.entity.AccountStatus;
import com.financialtransaction.account.entity.Currency;
import com.financialtransaction.account.entity.AccountType;
import com.financialtransaction.account.repository.AccountRepository;
import com.financialtransaction.user.entity.User;
import com.financialtransaction.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountCreationAttemptService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    public AccountCreationAttemptService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            AccountNumberGenerator accountNumberGenerator
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.accountNumberGenerator = accountNumberGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccountResponse createAccount(Long userId, AccountType accountType) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        Account account = new Account();

        account.setUser(user);
        account.setAccountNumber(accountNumberGenerator.generate());
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(accountType);

        Account saved = accountRepository.saveAndFlush(account);

        return AccountResponse.from(saved);
    }
}