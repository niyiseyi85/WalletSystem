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
public class UserAccountResponse {

    private Long userId;
    private String email;
    private String accountNumber;
    private BigDecimal balance;
}
