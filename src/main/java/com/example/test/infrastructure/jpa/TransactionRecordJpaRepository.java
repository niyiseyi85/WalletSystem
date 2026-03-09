package com.example.test.infrastructure.jpa;

import com.example.test.model.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRecordJpaRepository extends JpaRepository<TransactionRecord, Long> {

    List<TransactionRecord> findByFromAccountNumberOrToAccountNumberOrderByCreatedAtDesc(
            String fromAccountNumber, String toAccountNumber);
}
