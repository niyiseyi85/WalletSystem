package com.example.test.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Source account number must not be blank")
    @Pattern(regexp = "\\d{10}", message = "Source account number must be a 10-digit number")
    private String fromAccount;

    @NotBlank(message = "Destination account number must not be blank")
    @Pattern(regexp = "\\d{10}", message = "Destination account number must be a 10-digit number")
    private String toAccount;

    @NotNull(message = "Amount must not be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;
}
