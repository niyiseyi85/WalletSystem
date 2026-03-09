package com.example.test.controller;

import com.example.test.dto.request.CreateUserRequest;
import com.example.test.dto.request.TransferRequest;
import com.example.test.dto.response.ApiResponse;
import com.example.test.dto.response.PagedResponse;
import com.example.test.dto.response.TransactionRecordResponse;
import com.example.test.dto.response.TransferResponse;
import com.example.test.dto.response.UserAccountResponse;
import com.example.test.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Validated
@Slf4j
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserAccountResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        log.info("Received create user request for email: {}", request.getEmail());
        ApiResponse<UserAccountResponse> response = walletService.createUserAndAccount(request);
        return response.isSuccess()
                ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                : ResponseEntity.ok(response);
    }

    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @Valid @RequestBody TransferRequest request) {

        log.info("Received transfer request: {} -> {}, amount: {}",
                request.getFromAccount(), request.getToAccount(), request.getAmount());
        return ResponseEntity.ok(walletService.doTransfer(request));
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    public ResponseEntity<ApiResponse<UserAccountResponse>> getBalance(
            @PathVariable String accountNumber) {

        log.info("Received balance enquiry for account: {}", accountNumber);
        return ResponseEntity.ok(walletService.getAccountBalance(accountNumber));
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionRecordResponse>>> getTransactionHistory(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0")  @Min(0)        int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        log.info("Transaction history request: account={}, page={}, size={}",
                accountNumber, page, size);
        return ResponseEntity.ok(walletService.getTransactionHistory(accountNumber, page, size));
    }
}
