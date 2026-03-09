package com.example.test.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAccountResponse {

    private Long userId;
    private String email;
    private String accountNumber;
    private BigDecimal balance;
    private Instant createdAt;
    private Instant updatedAt;
}
