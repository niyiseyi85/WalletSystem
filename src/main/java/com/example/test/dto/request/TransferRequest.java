package com.example.test.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Source account number must not be blank")
    private String fromAccount;

    @NotBlank(message = "Destination account number must not be blank")
    private String toAccount;

    @NotNull(message = "Amount must not be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;
}
