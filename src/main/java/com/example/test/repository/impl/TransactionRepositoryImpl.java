package com.example.test.repository.impl;

import com.example.test.infrastructure.jpa.TransactionRecordJpaRepository;
import com.example.test.model.TransactionRecord;
import com.example.test.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    @Override
    public Page<TransactionRecord> findByAccountNumber(String accountNumber, Pageable pageable) {
        return transactionRecordJpaRepository
                .findByFromAccountNumberOrToAccountNumber(accountNumber, accountNumber, pageable);
    }

    @Override
    public Optional<TransactionRecord> findByTransactionRef(String transactionRef) {
        return transactionRecordJpaRepository.findByTransactionRef(transactionRef);
    }

    @Override
    public boolean existsByTransactionRef(String transactionRef) {
        return transactionRecordJpaRepository.existsByTransactionRef(transactionRef);
    }
}
