package com.example.test;

import com.example.test.dto.request.CreateUserRequest;
import com.example.test.dto.request.TransferRequest;
import com.example.test.dto.response.PagedResponse;
import com.example.test.dto.response.TransferResponse;
import com.example.test.dto.response.UserAccountResponse;
import com.example.test.exception.AccountNotFoundException;
import com.example.test.exception.DuplicateEmailException;
import com.example.test.exception.InsufficientFundsException;
import com.example.test.exception.InvalidTransferException;
import com.example.test.model.Account;
import com.example.test.model.TransactionRecord;
import com.example.test.model.User;
import com.example.test.repository.AccountRepository;
import com.example.test.repository.TransactionRepository;
import com.example.test.repository.UserRepository;
import com.example.test.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for WalletServiceImpl.
 *
 * No Spring context is loaded — all dependencies are mocked with Mockito.
 * Each test is isolated, fast, and tests exactly one behaviour.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Account buildAccount(String accountNumber, BigDecimal balance) {
        Account account = new Account();
        account.setId(1L);
        account.setAccountNumber(accountNumber);
        account.setBalance(balance);
        account.setVersion(0L);
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return account;
    }

    private User buildUser(String email, Account account) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setAccount(account);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createUserAndAccount
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createUserAndAccount")
    class CreateUserAndAccount {

        @Test
        @DisplayName("should create user with zero balance when email is new")
        void shouldCreateUser_success() {
            CreateUserRequest request = new CreateUserRequest("alice@example.com");

            Account account = buildAccount("1234567890", BigDecimal.ZERO);
            User savedUser = buildUser("alice@example.com", account);

            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserAccountResponse response = walletService.createUserAndAccount(request);

            assertThat(response.getEmail()).isEqualTo("alice@example.com");
            assertThat(response.getAccountNumber()).isEqualTo("1234567890");
            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getUserId()).isEqualTo(1L);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw DuplicateEmailException when email already registered")
        void shouldThrow_whenEmailAlreadyExists() {
            when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

            assertThatThrownBy(() ->
                    walletService.createUserAndAccount(new CreateUserRequest("bob@example.com")))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessageContaining("bob@example.com");

            verify(userRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // doTransfer — success path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("doTransfer")
    class DoTransfer {

        private Account sender;
        private Account receiver;

        @BeforeEach
        void setUp() {
            sender   = buildAccount("1000000001", new BigDecimal("1000.00"));
            receiver = buildAccount("2000000002", BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should debit sender and credit receiver when balance is sufficient")
        void shouldTransfer_success() {
            when(accountRepository.findByAccountNumberWithLock("1000000001"))
                    .thenReturn(Optional.of(sender));
            when(accountRepository.findByAccountNumberWithLock("2000000002"))
                    .thenReturn(Optional.of(receiver));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any(TransactionRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            TransferResponse result = walletService.doTransfer(
                    new TransferRequest("1000000001", "2000000002", new BigDecimal("400.00")));

            // Sender debited
            assertThat(result.getNewFromBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
            // Receiver credited
            assertThat(receiver.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
            assertThat(result.getTransactionRef()).isNotBlank();
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
        }

        @Test
        @DisplayName("should save a TransactionRecord with SUCCESS status after transfer")
        void shouldSaveAuditRecord_withSuccessStatus() {
            when(accountRepository.findByAccountNumberWithLock("1000000001"))
                    .thenReturn(Optional.of(sender));
            when(accountRepository.findByAccountNumberWithLock("2000000002"))
                    .thenReturn(Optional.of(receiver));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
            when(transactionRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            walletService.doTransfer(
                    new TransferRequest("1000000001", "2000000002", new BigDecimal("250.00")));

            TransactionRecord saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(TransactionRecord.TransactionStatus.SUCCESS);
            assertThat(saved.getFromAccountNumber()).isEqualTo("1000000001");
            assertThat(saved.getToAccountNumber()).isEqualTo("2000000002");
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
            assertThat(saved.getBalanceAfterDebit()).isEqualByComparingTo(new BigDecimal("750.00"));
            assertThat(saved.getTransactionRef()).isNotBlank();
        }

        @Test
        @DisplayName("should save exactly two account updates (debit + credit) per transfer")
        void shouldSaveBothAccounts_onTransfer() {
            when(accountRepository.findByAccountNumberWithLock("1000000001"))
                    .thenReturn(Optional.of(sender));
            when(accountRepository.findByAccountNumberWithLock("2000000002"))
                    .thenReturn(Optional.of(receiver));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            walletService.doTransfer(
                    new TransferRequest("1000000001", "2000000002", new BigDecimal("100.00")));

            verify(accountRepository, times(2)).save(any(Account.class));
        }

        // ── failure scenarios ────────────────────────────────────────────────

        @Test
        @DisplayName("should throw InsufficientFundsException when sender balance is too low")
        void shouldThrow_whenInsufficientBalance() {
            sender.setBalance(new BigDecimal("50.00")); // less than requested 200

            when(accountRepository.findByAccountNumberWithLock("1000000001"))
                    .thenReturn(Optional.of(sender));
            when(accountRepository.findByAccountNumberWithLock("2000000002"))
                    .thenReturn(Optional.of(receiver));

            assertThatThrownBy(() ->
                    walletService.doTransfer(
                            new TransferRequest("1000000001", "2000000002", new BigDecimal("200.00"))))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient funds")
                    .hasMessageContaining("50.00");

            // No money should have moved, no audit record saved
            verify(accountRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InsufficientFundsException when sender balance is exactly zero")
        void shouldThrow_whenBalanceIsZero() {
            sender.setBalance(BigDecimal.ZERO);

            when(accountRepository.findByAccountNumberWithLock("1000000001"))
                    .thenReturn(Optional.of(sender));
            when(accountRepository.findByAccountNumberWithLock("2000000002"))
                    .thenReturn(Optional.of(receiver));

            assertThatThrownBy(() ->
                    walletService.doTransfer(
                            new TransferRequest("1000000001", "2000000002", new BigDecimal("0.01"))))
                    .isInstanceOf(InsufficientFundsException.class);

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when source account does not exist")
        void shouldThrow_whenSourceAccountMissing() {
            when(accountRepository.findByAccountNumberWithLock("MISSING"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    walletService.doTransfer(
                            new TransferRequest("MISSING", "2000000002", new BigDecimal("100.00"))))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("MISSING");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when destination account does not exist")
        void shouldThrow_whenDestinationAccountMissing() {
            when(accountRepository.findByAccountNumberWithLock("1000000001"))
                    .thenReturn(Optional.of(sender));
            when(accountRepository.findByAccountNumberWithLock("MISSING"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    walletService.doTransfer(
                            new TransferRequest("1000000001", "MISSING", new BigDecimal("100.00"))))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("MISSING");

            verify(transactionRepository, never()).save(any());
        }

        // ── input validation ─────────────────────────────────────────────────

        @Test
        @DisplayName("should throw InvalidTransferException when source and destination are the same")
        void shouldThrow_whenSameAccount() {
            assertThatThrownBy(() ->
                    walletService.doTransfer(
                            new TransferRequest("1000000001", "1000000001", new BigDecimal("100.00"))))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("different");

            verify(accountRepository, never()).findByAccountNumberWithLock(anyString());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidTransferException when amount is zero")
        void shouldThrow_whenAmountIsZero() {
            assertThatThrownBy(() ->
                    walletService.doTransfer(
                            new TransferRequest("1000000001", "2000000002", BigDecimal.ZERO)))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("greater than zero");

            verify(accountRepository, never()).findByAccountNumberWithLock(anyString());
        }

        @Test
        @DisplayName("should throw InvalidTransferException when amount is negative")
        void shouldThrow_whenAmountIsNegative() {
            assertThatThrownBy(() ->
                    walletService.doTransfer(
                            new TransferRequest("1000000001", "2000000002", new BigDecimal("-1.00"))))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("greater than zero");

            verify(accountRepository, never()).findByAccountNumberWithLock(anyString());
        }

        @Test
        @DisplayName("should succeed when balance exactly equals the transfer amount (edge case)")
        void shouldTransfer_whenBalanceEqualsAmount() {
            sender.setBalance(new BigDecimal("100.00"));

            when(accountRepository.findByAccountNumberWithLock("1000000001"))
                    .thenReturn(Optional.of(sender));
            when(accountRepository.findByAccountNumberWithLock("2000000002"))
                    .thenReturn(Optional.of(receiver));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferResponse result = walletService.doTransfer(
                    new TransferRequest("1000000001", "2000000002", new BigDecimal("100.00")));

            assertThat(result.getNewFromBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(receiver.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getAccountBalance
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAccountBalance")
    class GetAccountBalance {

        @Test
        @DisplayName("should return balance and timestamps for existing account")
        void shouldReturnBalance_success() {
            Account account = buildAccount("1234567890", new BigDecimal("999.99"));
            when(accountRepository.findByAccountNumber("1234567890"))
                    .thenReturn(Optional.of(account));

            UserAccountResponse response = walletService.getAccountBalance("1234567890");

            assertThat(response.getAccountNumber()).isEqualTo("1234567890");
            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("999.99"));
            assertThat(response.getCreatedAt()).isNotNull();
            assertThat(response.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw AccountNotFoundException for unknown account")
        void shouldThrow_whenAccountNotFound() {
            when(accountRepository.findByAccountNumber("UNKNOWN"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.getAccountBalance("UNKNOWN"))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("UNKNOWN");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getTransactionHistory
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTransactionHistory")
    class GetTransactionHistory {

        @Test
        @DisplayName("should return mapped transaction history for existing account")
        void shouldReturnHistory_success() {
            Account account = buildAccount("1234567890", BigDecimal.ZERO);
            when(accountRepository.findByAccountNumber("1234567890"))
                    .thenReturn(Optional.of(account));

            TransactionRecord record = TransactionRecord.builder()
                    .id(1L)
                    .transactionRef("ref-001")
                    .fromAccountNumber("1234567890")
                    .toAccountNumber("9876543210")
                    .amount(new BigDecimal("500.00"))
                    .balanceAfterDebit(new BigDecimal("500.00"))
                    .status(TransactionRecord.TransactionStatus.SUCCESS)
                    .build();

            when(transactionRepository.findByAccountNumber("1234567890"))
                    .thenReturn(List.of(record));

            var history = walletService.getTransactionHistory("1234567890");

            assertThat(history).hasSize(1);
            assertThat(history.get(0).getTransactionRef()).isEqualTo("ref-001");
            assertThat(history.get(0).getFromAccount()).isEqualTo("1234567890");
            assertThat(history.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(history.get(0).getStatus())
                    .isEqualTo(TransactionRecord.TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("should return empty list when no transactions exist")
        void shouldReturnEmptyList_whenNoHistory() {
            Account account = buildAccount("1234567890", BigDecimal.ZERO);
            when(accountRepository.findByAccountNumber("1234567890"))
                    .thenReturn(Optional.of(account));
            when(transactionRepository.findByAccountNumber("1234567890"))
                    .thenReturn(List.of());

            var history = walletService.getTransactionHistory("1234567890");

            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("should throw AccountNotFoundException for unknown account")
        void shouldThrow_whenAccountNotFound() {
            when(accountRepository.findByAccountNumber("UNKNOWN"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.getTransactionHistory("UNKNOWN"))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("UNKNOWN");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getTransactionHistory (paginated)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTransactionHistory (paginated)")
    class GetTransactionHistoryPaged {

        @Test
        @DisplayName("should return paginated response with correct metadata")
        void shouldReturnPagedResponse_success() {
            Account account = buildAccount("1234567890", BigDecimal.ZERO);
            when(accountRepository.findByAccountNumber("1234567890"))
                    .thenReturn(Optional.of(account));

            TransactionRecord record = TransactionRecord.builder()
                    .id(1L)
                    .transactionRef("ref-001")
                    .fromAccountNumber("1234567890")
                    .toAccountNumber("9876543210")
                    .amount(new BigDecimal("200.00"))
                    .balanceAfterDebit(new BigDecimal("800.00"))
                    .status(TransactionRecord.TransactionStatus.SUCCESS)
                    .build();

            when(transactionRepository.findByAccountNumber(eq("1234567890"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1));

            PagedResponse<com.example.test.dto.response.TransactionRecordResponse> result =
                    walletService.getTransactionHistory("1234567890", 0, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getPage()).isZero();
            assertThat(result.getSize()).isEqualTo(20);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.isLast()).isTrue();
            assertThat(result.getContent().get(0).getTransactionRef()).isEqualTo("ref-001");
        }

        @Test
        @DisplayName("should return empty paginated response when no transactions exist")
        void shouldReturnEmptyPagedResponse_whenNoHistory() {
            Account account = buildAccount("1234567890", BigDecimal.ZERO);
            when(accountRepository.findByAccountNumber("1234567890"))
                    .thenReturn(Optional.of(account));

            when(transactionRepository.findByAccountNumber(eq("1234567890"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            PagedResponse<com.example.test.dto.response.TransactionRecordResponse> result =
                    walletService.getTransactionHistory("1234567890", 0, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("should cap page size at 100 when size exceeds limit")
        void shouldCapPageSize_whenSizeExceedsMax() {
            Account account = buildAccount("1234567890", BigDecimal.ZERO);
            when(accountRepository.findByAccountNumber("1234567890"))
                    .thenReturn(Optional.of(account));

            // Size passed is 999 — service should cap to 100
            when(transactionRepository.findByAccountNumber(anyString(), any(Pageable.class)))
                    .thenAnswer(inv -> {
                        Pageable p = inv.getArgument(1);
                        assertThat(p.getPageSize()).isEqualTo(100);
                        return new PageImpl<>(List.of(), p, 0);
                    });

            walletService.getTransactionHistory("1234567890", 0, 999);
            // assertion is inside the answer stub above
        }

        @Test
        @DisplayName("should throw AccountNotFoundException for unknown account (paginated)")
        void shouldThrow_whenAccountNotFound_paginated() {
            when(accountRepository.findByAccountNumber("UNKNOWN"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.getTransactionHistory("UNKNOWN", 0, 20))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("UNKNOWN");
        }
    }
}
