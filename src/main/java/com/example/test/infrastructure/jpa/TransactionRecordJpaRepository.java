package com.example.test.infrastructure.jpa;

import com.example.test.model.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRecordJpaRepository extends JpaRepository<TransactionRecord, Long> {

    List<TransactionRecord> findByFromAccountNumberOrToAccountNumberOrderByCreatedAtDesc(
            String fromAccountNumber, String toAccountNumber);

    Optional<TransactionRecord> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);
}
