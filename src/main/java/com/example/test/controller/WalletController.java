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

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Validated
@Slf4j
public class WalletController {

    private final WalletService walletService;

    /**
     * POST /api/v1/wallet/users
     * Creates a new user and generates an account with a zero wallet balance.
     */
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserAccountResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        log.info("Received create user request for email: {}", request.getEmail());
        UserAccountResponse response = walletService.createUserAndAccount(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User and account created successfully", response));
    }

    /**
     * POST /api/v1/wallet/transfers
     * Transfers funds between two accounts.
     */
    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @Valid @RequestBody TransferRequest request) {

        log.info("Received transfer request: {} -> {}, amount: {}",
                request.getFromAccount(), request.getToAccount(), request.getAmount());
        TransferResponse response = walletService.doTransfer(request);
        return ResponseEntity
                .ok(ApiResponse.success("Transfer completed successfully", response));
    }

    /**
     * GET /api/v1/wallet/accounts/{accountNumber}/balance
     * Returns the current balance for an account.
     */
    @GetMapping("/accounts/{accountNumber}/balance")
    public ResponseEntity<ApiResponse<UserAccountResponse>> getBalance(
            @PathVariable String accountNumber) {

        log.info("Received balance enquiry for account: {}", accountNumber);
        UserAccountResponse response = walletService.getAccountBalance(accountNumber);
        return ResponseEntity
                .ok(ApiResponse.success("Account balance retrieved successfully", response));
    }

    /**
     * GET /api/v1/wallet/accounts/{accountNumber}/transactions
     * Returns a paginated page of transaction history, most recent first.
     *
     * Query params:
     *   page  - 0-based page index (default 0)
     *   size  - records per page (default 20, max 100)
     */
    @GetMapping("/accounts/{accountNumber}/transactions")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionRecordResponse>>> getTransactionHistory(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0")  @Min(0)        int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        log.info("Transaction history request: account={}, page={}, size={}",
                accountNumber, page, size);
        PagedResponse<TransactionRecordResponse> response =
                walletService.getTransactionHistory(accountNumber, page, size);
        return ResponseEntity
                .ok(ApiResponse.success("Transaction history retrieved successfully", response));
    }
}
