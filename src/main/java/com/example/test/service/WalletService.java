package com.example.test.service;

import com.example.test.dto.request.CreateUserRequest;
import com.example.test.dto.request.TransferRequest;
import com.example.test.dto.response.ApiResponse;
import com.example.test.dto.response.PagedResponse;
import com.example.test.dto.response.TransactionRecordResponse;
import com.example.test.dto.response.TransferResponse;
import com.example.test.dto.response.UserAccountResponse;

import java.util.List;

public interface WalletService {

    ApiResponse<UserAccountResponse> createUserAndAccount(CreateUserRequest request);

    ApiResponse<TransferResponse> doTransfer(TransferRequest request);

    ApiResponse<UserAccountResponse> getAccountBalance(String accountNumber);

    ApiResponse<PagedResponse<TransactionRecordResponse>> getTransactionHistory(
            String accountNumber, int page, int size);

    ApiResponse<List<TransactionRecordResponse>> getTransactionHistory(String accountNumber);
}
