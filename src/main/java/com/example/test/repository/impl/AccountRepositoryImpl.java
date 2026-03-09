package com.example.test.repository.impl;

import com.example.test.infrastructure.jpa.AccountJpaRepository;
import com.example.test.model.Account;
import com.example.test.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountJpaRepository accountJpaRepository;

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return accountJpaRepository.findByAccountNumber(accountNumber);
    }

    @Override
    public Account save(Account account) {
        return accountJpaRepository.save(account);
    }
}
