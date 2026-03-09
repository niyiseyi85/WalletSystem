package com.example.test.repository.impl;

import com.example.test.infrastructure.jpa.TransactionRecordJpaRepository;
import com.example.test.model.TransactionRecord;
import com.example.test.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {

    private final TransactionRecordJpaRepository transactionRecordJpaRepository;

    @Override
    public TransactionRecord save(TransactionRecord record) {
        return transactionRecordJpaRepository.save(record);
    }

    @Override
    public List<TransactionRecord> findByAccountNumber(String accountNumber) {
        return transactionRecordJpaRepository
                .findByFromAccountNumberOrToAccountNumberOrderByCreatedAtDesc(
                        accountNumber, accountNumber);
    }
}
