package com.example.test.infrastructure.jpa;

import com.example.test.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);
}
