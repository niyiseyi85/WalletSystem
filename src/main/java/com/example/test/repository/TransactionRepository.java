package com.example.test.repository;

import com.example.test.model.TransactionRecord;

import java.util.List;

public interface TransactionRepository {

    TransactionRecord save(TransactionRecord record);

    List<TransactionRecord> findByAccountNumber(String accountNumber);
}
