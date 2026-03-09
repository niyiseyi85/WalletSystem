package com.example.test.infrastructure.jpa;

import com.example.test.model.TransactionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRecordJpaRepository extends JpaRepository<TransactionRecord, Long> {

    List<TransactionRecord> findByFromAccountNumberOrToAccountNumberOrderByCreatedAtDesc(
            String fromAccountNumber, String toAccountNumber);

    /**
     * Paginated query — returns one page of transactions for an account (as sender or receiver),
     * ordered most-recent first. Sort is driven by the Pageable argument.
     */
    Page<TransactionRecord> findByFromAccountNumberOrToAccountNumber(
            String fromAccountNumber, String toAccountNumber, Pageable pageable);

    Optional<TransactionRecord> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);
}
