package com.example.test.dto.response;

import com.example.test.model.TransactionRecord.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionRecordResponse {

    private Long id;
    private String transactionRef;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private BigDecimal balanceAfterDebit;
    private TransactionStatus status;
    private Instant createdAt;
}
