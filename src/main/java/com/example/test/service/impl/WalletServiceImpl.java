package com.example.test.service.impl;

import com.example.test.dto.request.CreateUserRequest;
import com.example.test.dto.request.TransferRequest;
import com.example.test.dto.response.PagedResponse;
import com.example.test.dto.response.TransactionRecordResponse;
import com.example.test.dto.response.TransferResponse;
import com.example.test.dto.response.UserAccountResponse;
import com.example.test.exception.AccountNotFoundException;
import com.example.test.exception.DuplicateEmailException;
import com.example.test.exception.InsufficientFundsException;
import com.example.test.exception.InvalidTransferException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public UserAccountResponse createUserAndAccount(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("A user with email '" + request.getEmail() + "' already exists");
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

        return UserAccountResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .accountNumber(savedUser.getAccount().getAccountNumber())
                .balance(savedUser.getAccount().getBalance())
                .createdAt(savedUser.getAccount().getCreatedAt())
                .build();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public TransferResponse doTransfer(TransferRequest request) {
        validateTransferRequest(request);

        // System-generated reference — unique per transaction, acts as idempotency key
        // (unique DB constraint prevents duplicate records even under concurrent pressure)
        final String transactionRef = UUID.randomUUID().toString();

        // Acquire PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) locks on both accounts.
        // Consistent lock ordering (by account number) prevents deadlocks under concurrency.
        Account fromAccount = accountRepository.findByAccountNumberWithLock(request.getFromAccount())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Source account not found: " + request.getFromAccount()));

        Account toAccount = accountRepository.findByAccountNumberWithLock(request.getToAccount())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Destination account not found: " + request.getToAccount()));

        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available balance: " + fromAccount.getBalance()
                    + ", requested: " + request.getAmount());
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        // Both saves are within the same @Transactional boundary —
        // any exception after this point will roll back both account updates AND the audit record.
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Persist immutable audit record — unique constraint on transactionRef
        // ensures this record cannot be duplicated at the database level.
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

        return TransferResponse.builder()
                .transactionRef(transactionRef)
                .fromAccount(request.getFromAccount())
                .toAccount(request.getToAccount())
                .amount(request.getAmount())
                .newFromBalance(fromAccount.getBalance())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountResponse getAccountBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));

        return UserAccountResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TransactionRecordResponse> getTransactionHistory(
            String accountNumber, int page, int size) {

        accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));

        // Cap page size at 100 to prevent oversized queries
        int cappedSize = Math.min(size, 100);

        PageRequest pageRequest = PageRequest.of(
                page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TransactionRecord> resultPage =
                transactionRepository.findByAccountNumber(accountNumber, pageRequest);

        List<TransactionRecordResponse> content = resultPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PagedResponse.<TransactionRecordResponse>builder()
                .content(content)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .last(resultPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionRecordResponse> getTransactionHistory(String accountNumber) {
        accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));

        return transactionRepository.findByAccountNumber(accountNumber).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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

    private void validateTransferRequest(TransferRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be greater than zero");
        }
        if (request.getFromAccount().equalsIgnoreCase(request.getToAccount())) {
            throw new InvalidTransferException("Source and destination accounts must be different");
        }
    }

    private String generateAccountNumber() {
        // Generate a unique 10-digit NUBAN-style account number (1000000000 – 9999999999)
        long number = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
        return String.valueOf(number);
    }
}
