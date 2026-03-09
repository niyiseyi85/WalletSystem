package com.example.test.repository;

import com.example.test.model.Account;

import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findByAccountNumber(String accountNumber);

    Account save(Account account);
}
