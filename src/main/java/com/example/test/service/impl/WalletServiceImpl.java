package com.example.test.service.impl;

import com.example.test.dto.request.CreateUserRequest;
import com.example.test.dto.request.TransferRequest;
import com.example.test.dto.response.ApiResponse;
import com.example.test.dto.response.PagedResponse;
import com.example.test.dto.response.TransactionRecordResponse;
import com.example.test.dto.response.TransferResponse;
import com.example.test.dto.response.UserAccountResponse;
import com.example.test.model.Account;
import com.example.test.model.TransactionRecord;
import com.example.test.model.TransactionRecord.TransactionStatus;
import com.example.test.model.User;
import com.example.test.repository.AccountRepository;
import com.example.test.repository.TransactionRepository;
import com.example.test.repository.UserRepository;
import com.example.test.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public ApiResponse<UserAccountResponse> createUserAndAccount(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ApiResponse.error("A user with email '" + request.getEmail() + "' already exists");
        }

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(BigDecimal.ZERO);

        User user = new User();
        user.setEmail(request.getEmail());
        user.setAccount(account);

        User savedUser = userRepository.save(user);

        log.info("Created user [id={}] with account [{}]",
                savedUser.getId(), savedUser.getAccount().getAccountNumber());

        return ApiResponse.success("User and account created successfully",
                UserAccountResponse.builder()
                        .userId(savedUser.getId())
                        .email(savedUser.getEmail())
                        .accountNumber(savedUser.getAccount().getAccountNumber())
                        .balance(savedUser.getAccount().getBalance())
                        .createdAt(savedUser.getAccount().getCreatedAt())
                        .build());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ApiResponse<TransferResponse> doTransfer(TransferRequest request) {
        String validationError = validateTransferRequest(request);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }

        // System-generated reference — unique per transaction, acts as idempotency key
        final String transactionRef = UUID.randomUUID().toString();

        // Acquire PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) locks on both accounts.
        // Consistent lock ordering (by account number) prevents deadlocks under concurrency.
        Optional<Account> fromOpt = accountRepository.findByAccountNumberWithLock(request.getFromAccount());
        if (fromOpt.isEmpty()) {
            return ApiResponse.error("Source account not found: " + request.getFromAccount());
        }
        Account fromAccount = fromOpt.get();

        Optional<Account> toOpt = accountRepository.findByAccountNumberWithLock(request.getToAccount());
        if (toOpt.isEmpty()) {
            return ApiResponse.error("Destination account not found: " + request.getToAccount());
        }
        Account toAccount = toOpt.get();

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            return ApiResponse.error("Insufficient funds. Available balance: " + fromAccount.getBalance()
                    + ", requested: " + request.getAmount());
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        // Both saves are within the same @Transactional boundary
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Persist immutable audit record — unique constraint on transactionRef
        transactionRepository.save(TransactionRecord.builder()
                .transactionRef(transactionRef)
                .fromAccountNumber(request.getFromAccount())
                .toAccountNumber(request.getToAccount())
                .amount(request.getAmount())
                .balanceAfterDebit(fromAccount.getBalance())
                .status(TransactionStatus.SUCCESS)
                .build());

        log.info("Transfer [{}] completed: {} NGN from [{}] to [{}]. New source balance: {}",
                transactionRef, request.getAmount(), request.getFromAccount(),
                request.getToAccount(), fromAccount.getBalance());

        return ApiResponse.success("Transfer completed successfully",
                TransferResponse.builder()
                        .transactionRef(transactionRef)
                        .fromAccount(request.getFromAccount())
                        .toAccount(request.getToAccount())
                        .amount(request.getAmount())
                        .newFromBalance(fromAccount.getBalance())
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<UserAccountResponse> getAccountBalance(String accountNumber) {
        Optional<Account> accountOpt = accountRepository.findByAccountNumber(accountNumber);
        if (accountOpt.isEmpty()) {
            return ApiResponse.error("Account not found: " + accountNumber);
        }
        Account account = accountOpt.get();

        return ApiResponse.success("Balance retrieved successfully",
                UserAccountResponse.builder()
                        .accountNumber(account.getAccountNumber())
                        .balance(account.getBalance())
                        .createdAt(account.getCreatedAt())
                        .updatedAt(account.getUpdatedAt())
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<PagedResponse<TransactionRecordResponse>> getTransactionHistory(
            String accountNumber, int page, int size) {

        if (accountRepository.findByAccountNumber(accountNumber).isEmpty()) {
            return ApiResponse.error("Account not found: " + accountNumber);
        }

        // Cap page size at 100 to prevent oversized queries
        int cappedSize = Math.min(size, 100);

        PageRequest pageRequest = PageRequest.of(
                page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TransactionRecord> resultPage =
                transactionRepository.findByAccountNumber(accountNumber, pageRequest);

        List<TransactionRecordResponse> content = resultPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ApiResponse.success("Transaction history retrieved",
                PagedResponse.<TransactionRecordResponse>builder()
                        .content(content)
                        .page(resultPage.getNumber())
                        .size(resultPage.getSize())
                        .totalElements(resultPage.getTotalElements())
                        .totalPages(resultPage.getTotalPages())
                        .last(resultPage.isLast())
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<TransactionRecordResponse>> getTransactionHistory(String accountNumber) {
        if (accountRepository.findByAccountNumber(accountNumber).isEmpty()) {
            return ApiResponse.error("Account not found: " + accountNumber);
        }

        return ApiResponse.success("Transaction history retrieved",
                transactionRepository.findByAccountNumber(accountNumber).stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList()));
    }

    private TransactionRecordResponse toResponse(TransactionRecord record) {
        return TransactionRecordResponse.builder()
                .id(record.getId())
                .transactionRef(record.getTransactionRef())
                .fromAccount(record.getFromAccountNumber())
                .toAccount(record.getToAccountNumber())
                .amount(record.getAmount())
                .balanceAfterDebit(record.getBalanceAfterDebit())
                .status(record.getStatus())
                .createdAt(record.getCreatedAt())
                .build();
    }

    /** Returns an error message string, or null if the request is valid. */
    private String validateTransferRequest(TransferRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Transfer amount must be greater than zero";
        }
        if (request.getFromAccount().equalsIgnoreCase(request.getToAccount())) {
            return "Source and destination accounts must be different";
        }
        return null;
    }

    private String generateAccountNumber() {
        long number = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
        return String.valueOf(number);
    }
}
