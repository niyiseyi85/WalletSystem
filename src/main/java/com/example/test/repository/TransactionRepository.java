package com.example.test.repository;

import com.example.test.model.TransactionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    TransactionRecord save(TransactionRecord record);

    List<TransactionRecord> findByAccountNumber(String accountNumber);

    Page<TransactionRecord> findByAccountNumber(String accountNumber, Pageable pageable);

    Optional<TransactionRecord> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);
}
