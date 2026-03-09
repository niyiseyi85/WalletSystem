package com.example.test.service.impl;

import com.example.test.dto.request.CreateUserRequest;
import com.example.test.dto.request.TransferRequest;
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
import com.example.test.model.WalletBalance;
import com.example.test.repository.AccountRepository;
import com.example.test.repository.TransactionRepository;
import com.example.test.repository.UserRepository;
import com.example.test.repository.WalletBalanceRepository;
import com.example.test.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final WalletBalanceRepository walletBalanceRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public UserAccountResponse createUserAndAccount(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("A user with email '" + request.getEmail() + "' already exists");
        }

        WalletBalance walletBalance = new WalletBalance();
        walletBalance.setAmount(BigDecimal.ZERO);

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setWalletBalance(walletBalance);

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
                .balance(savedUser.getAccount().getWalletBalance().getAmount())
                .build();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse doTransfer(TransferRequest request) {
        validateTransferRequest(request);

        // Idempotency check — if this transactionRef was already processed, return the cached result
        Optional<TransactionRecord> existing = transactionRepository.findByTransactionRef(request.getTransactionRef());
        if (existing.isPresent()) {
            TransactionRecord cached = existing.get();
            log.info("Idempotent request for transactionRef [{}] — returning cached result", request.getTransactionRef());
            return TransferResponse.builder()
                    .transactionRef(cached.getTransactionRef())
                    .fromAccount(cached.getFromAccountNumber())
                    .toAccount(cached.getToAccountNumber())
                    .amount(cached.getAmount())
                    .newFromBalance(cached.getBalanceAfterDebit())
                    .build();
        }

        Account fromAccount = accountRepository.findByAccountNumber(request.getFromAccount())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Source account not found: " + request.getFromAccount()));

        Account toAccount = accountRepository.findByAccountNumber(request.getToAccount())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Destination account not found: " + request.getToAccount()));

        // Acquire pessimistic write locks to prevent concurrent balance manipulation
        WalletBalance fromBalance = walletBalanceRepository
                .findByIdWithLock(fromAccount.getWalletBalance().getId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Wallet balance not found for account: " + request.getFromAccount()));

        WalletBalance toBalance = walletBalanceRepository
                .findByIdWithLock(toAccount.getWalletBalance().getId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Wallet balance not found for account: " + request.getToAccount()));

        if (fromBalance.getAmount().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available balance: " + fromBalance.getAmount()
                    + ", requested: " + request.getAmount());
        }

        fromBalance.setAmount(fromBalance.getAmount().subtract(request.getAmount()));
        toBalance.setAmount(toBalance.getAmount().add(request.getAmount()));

        walletBalanceRepository.save(fromBalance);
        walletBalanceRepository.save(toBalance);

        // Persist audit record for this transfer
        transactionRepository.save(TransactionRecord.builder()
                .transactionRef(request.getTransactionRef())
                .fromAccountNumber(request.getFromAccount())
                .toAccountNumber(request.getToAccount())
                .amount(request.getAmount())
                .balanceAfterDebit(fromBalance.getAmount())
                .status(TransactionStatus.SUCCESS)
                .build());

        log.info("Transfer completed: {} NGN from [{}] to [{}]. New source balance: {}",
                request.getAmount(), request.getFromAccount(),
                request.getToAccount(), fromBalance.getAmount());

        return TransferResponse.builder()
                .transactionRef(request.getTransactionRef())
                .fromAccount(request.getFromAccount())
                .toAccount(request.getToAccount())
                .amount(request.getAmount())
                .newFromBalance(fromBalance.getAmount())
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
                .balance(account.getWalletBalance().getAmount())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionRecordResponse> getTransactionHistory(String accountNumber) {
        accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));

        return transactionRepository.findByAccountNumber(accountNumber).stream()
                .map(record -> TransactionRecordResponse.builder()
                        .id(record.getId())
                        .transactionRef(record.getTransactionRef())
                        .fromAccount(record.getFromAccountNumber())
                        .toAccount(record.getToAccountNumber())
                        .amount(record.getAmount())
                        .balanceAfterDebit(record.getBalanceAfterDebit())
                        .status(record.getStatus())
                        .createdAt(record.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
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
