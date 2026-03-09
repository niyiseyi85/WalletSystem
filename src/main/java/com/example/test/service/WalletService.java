package com.example.test.service;

import com.example.test.dto.request.CreateUserRequest;
import com.example.test.dto.request.TransferRequest;
import com.example.test.dto.response.PagedResponse;
import com.example.test.dto.response.TransactionRecordResponse;
import com.example.test.dto.response.TransferResponse;
import com.example.test.dto.response.UserAccountResponse;

import java.util.List;

public interface WalletService {

    /**
     * Creates a new user and provisions a linked account with a zero wallet balance.
     *
     * @param request the user creation payload containing email
     * @return details of the newly created user and account
     */
    UserAccountResponse createUserAndAccount(CreateUserRequest request);

    /**
     * Transfers funds between two accounts within the system.
     * The operation is atomic — both debit and credit succeed or neither occurs.
     *
     * @param request the transfer payload with source account, destination account, and amount
     * @return details of the completed transfer including the updated source balance
     */
    TransferResponse doTransfer(TransferRequest request);

    /**
     * Retrieves the current balance for a given account number.
     *
     * @param accountNumber the account to query
     * @return account and current balance details
     */
    UserAccountResponse getAccountBalance(String accountNumber);

    /**
     * Retrieves a paginated page of transaction history for a given account number,
     * ordered most recent first.
     *
     * @param accountNumber the account to query
     * @param page          0-based page number (default 0)
     * @param size          number of records per page (default 20, max 100)
     * @return paginated wrapper containing the records and pagination metadata
     */
    PagedResponse<TransactionRecordResponse> getTransactionHistory(
            String accountNumber, int page, int size);

    /**
     * Non-paginated transaction history — used internally by tests.
     */
    List<TransactionRecordResponse> getTransactionHistory(String accountNumber);
}
