package com.financialtransaction.account.service;

import com.financialtransaction.account.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {


    private static final int ACCOUNT_NUMBER_LENGTH = 12;
    private static final int MAX_ATTEMPTS = 10;

    private final AccountRepository accountRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String generate() {

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {

            StringBuilder accountNumber = new StringBuilder(
                    ACCOUNT_NUMBER_LENGTH
            );

            accountNumber.append(
                    secureRandom.nextInt(9) + 1
            );

            for (int i = 1; i < ACCOUNT_NUMBER_LENGTH; i++) {
                accountNumber.append(secureRandom.nextInt(10));
            }

            String candidate = accountNumber.toString();

            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Unable to generate a unique account number"
        );
    }


}
