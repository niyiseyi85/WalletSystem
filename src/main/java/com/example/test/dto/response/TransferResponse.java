package com.example.test.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferResponse {

    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private BigDecimal newFromBalance;
}
