package com.example.test.repository;

import com.example.test.model.Account;

import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Acquires a PESSIMISTIC_WRITE lock on the account row.
     * Must be called within an active @Transactional context.
     */
    Optional<Account> findByAccountNumberWithLock(String accountNumber);

    Account save(Account account);
}
