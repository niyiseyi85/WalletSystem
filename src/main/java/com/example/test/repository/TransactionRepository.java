package com.example.test.repository;

import com.example.test.model.TransactionRecord;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    TransactionRecord save(TransactionRecord record);

    List<TransactionRecord> findByAccountNumber(String accountNumber);

    Optional<TransactionRecord> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);
}
