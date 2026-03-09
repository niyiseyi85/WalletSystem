package com.example.test;

import com.example.test.dto.request.CreateUserRequest;
import com.example.test.dto.request.TransferRequest;
import com.example.test.dto.response.TransactionRecordResponse;
import com.example.test.dto.response.TransferResponse;
import com.example.test.dto.response.UserAccountResponse;
import com.example.test.exception.AccountNotFoundException;
import com.example.test.exception.DuplicateEmailException;
import com.example.test.exception.InsufficientFundsException;
import com.example.test.exception.InvalidTransferException;
import com.example.test.infrastructure.jpa.AccountJpaRepository;
import com.example.test.model.TransactionRecord.TransactionStatus;
import com.example.test.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WalletServiceIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    // ─────────────────────────────────────────────
    // createUserAndAccount
    // ─────────────────────────────────────────────

    @Test
    void shouldCreateUserWithAccountAndZeroBalance() {
        UserAccountResponse response = walletService.createUserAndAccount(
                new CreateUserRequest("alice@example.com"));

        assertThat(response.getUserId()).isNotNull();
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getAccountNumber()).matches("\\d{10}");
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldThrowDuplicateEmailException_whenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest("duplicate@example.com");
        walletService.createUserAndAccount(request);

        assertThatThrownBy(() -> walletService.createUserAndAccount(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("duplicate@example.com");
    }

    // ─────────────────────────────────────────────
    // doTransfer
    // ─────────────────────────────────────────────

    @Test
    void shouldTransferSuccessfully_whenBalanceIsSufficient() {
        UserAccountResponse userA = walletService.createUserAndAccount(
                new CreateUserRequest("usera@example.com"));
        UserAccountResponse userB = walletService.createUserAndAccount(
                new CreateUserRequest("userb@example.com"));

        seedBalance(userA.getAccountNumber(), new BigDecimal("1000.00"));

        TransferResponse result = walletService.doTransfer(
                new TransferRequest(userA.getAccountNumber(), userB.getAccountNumber(), new BigDecimal("400.00")));

        assertThat(result.getNewFromBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(result.getFromAccount()).isEqualTo(userA.getAccountNumber());
        assertThat(result.getToAccount()).isEqualTo(userB.getAccountNumber());
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(result.getTransactionRef()).isNotBlank();
    }

    @Test
    void shouldThrowInsufficientFundsException_whenBalanceIsLow() {
        UserAccountResponse sender = walletService.createUserAndAccount(
                new CreateUserRequest("poor@example.com"));
        UserAccountResponse receiver = walletService.createUserAndAccount(
                new CreateUserRequest("rich@example.com"));

        assertThatThrownBy(() -> walletService.doTransfer(
                new TransferRequest(sender.getAccountNumber(), receiver.getAccountNumber(), new BigDecimal("0.01"))))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void shouldThrowAccountNotFoundException_whenSourceAccountDoesNotExist() {
        UserAccountResponse receiver = walletService.createUserAndAccount(
                new CreateUserRequest("receiver2@example.com"));

        assertThatThrownBy(() -> walletService.doTransfer(
                new TransferRequest("NOTEXIST001", receiver.getAccountNumber(), new BigDecimal("100.00"))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("NOTEXIST001");
    }

    @Test
    void shouldThrowAccountNotFoundException_whenDestinationAccountDoesNotExist() {
        UserAccountResponse sender = walletService.createUserAndAccount(
                new CreateUserRequest("sender2@example.com"));

        assertThatThrownBy(() -> walletService.doTransfer(
                new TransferRequest(sender.getAccountNumber(), "NOTEXIST001", new BigDecimal("100.00"))))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("NOTEXIST001");
    }

    @Test
    void shouldThrowInvalidTransferException_whenTransferringToSameAccount() {
        UserAccountResponse user = walletService.createUserAndAccount(
                new CreateUserRequest("self@example.com"));

        assertThatThrownBy(() -> walletService.doTransfer(
                new TransferRequest(user.getAccountNumber(), user.getAccountNumber(), new BigDecimal("100.00"))))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("different");
    }

    @Test
    void shouldThrowInvalidTransferException_whenAmountIsZero() {
        UserAccountResponse userA = walletService.createUserAndAccount(
                new CreateUserRequest("userx@example.com"));
        UserAccountResponse userB = walletService.createUserAndAccount(
                new CreateUserRequest("usery@example.com"));

        assertThatThrownBy(() -> walletService.doTransfer(
                new TransferRequest(userA.getAccountNumber(), userB.getAccountNumber(), BigDecimal.ZERO)))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("greater than zero");
    }

    // ─────────────────────────────────────────────
    // getAccountBalance
    // ─────────────────────────────────────────────

    @Test
    void shouldReturnCurrentBalance() {
        UserAccountResponse user = walletService.createUserAndAccount(
                new CreateUserRequest("balance@example.com"));
        seedBalance(user.getAccountNumber(), new BigDecimal("750.00"));

        UserAccountResponse balance = walletService.getAccountBalance(user.getAccountNumber());

        assertThat(balance.getAccountNumber()).isEqualTo(user.getAccountNumber());
        assertThat(balance.getBalance()).isEqualByComparingTo(new BigDecimal("750.00"));
    }

    @Test
    void shouldThrowAccountNotFoundException_whenBalanceQueryOnUnknownAccount() {
        assertThatThrownBy(() -> walletService.getAccountBalance("NOTEXIST002"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("NOTEXIST002");
    }

    // ─────────────────────────────────────────────
    // getTransactionHistory
    // ─────────────────────────────────────────────

    @Test
    void shouldReturnTransactionHistory_afterTransfer() {
        UserAccountResponse userA = walletService.createUserAndAccount(
                new CreateUserRequest("histA@example.com"));
        UserAccountResponse userB = walletService.createUserAndAccount(
                new CreateUserRequest("histB@example.com"));

        seedBalance(userA.getAccountNumber(), new BigDecimal("500.00"));
        walletService.doTransfer(
                new TransferRequest(userA.getAccountNumber(), userB.getAccountNumber(), new BigDecimal("200.00")));

        List<TransactionRecordResponse> history = walletService.getTransactionHistory(userA.getAccountNumber());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromAccount()).isEqualTo(userA.getAccountNumber());
        assertThat(history.get(0).getToAccount()).isEqualTo(userB.getAccountNumber());
        assertThat(history.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(history.get(0).getBalanceAfterDebit()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(history.get(0).getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void shouldReturnEmptyHistory_whenNoTransfersHaveOccurred() {
        UserAccountResponse user = walletService.createUserAndAccount(
                new CreateUserRequest("notransfer@example.com"));

        List<TransactionRecordResponse> history = walletService.getTransactionHistory(user.getAccountNumber());

        assertThat(history).isEmpty();
    }

    @Test
    void shouldGenerateUniqueTransactionRef_forEachTransfer() {
        UserAccountResponse userA = walletService.createUserAndAccount(
                new CreateUserRequest("ref_a@example.com"));
        UserAccountResponse userB = walletService.createUserAndAccount(
                new CreateUserRequest("ref_b@example.com"));

        seedBalance(userA.getAccountNumber(), new BigDecimal("2000.00"));

        TransferResponse first = walletService.doTransfer(
                new TransferRequest(userA.getAccountNumber(), userB.getAccountNumber(), new BigDecimal("100.00")));

        seedBalance(userA.getAccountNumber(), new BigDecimal("2000.00"));

        TransferResponse second = walletService.doTransfer(
                new TransferRequest(userA.getAccountNumber(), userB.getAccountNumber(), new BigDecimal("100.00")));

        assertThat(first.getTransactionRef()).isNotBlank();
        assertThat(second.getTransactionRef()).isNotBlank();
        assertThat(first.getTransactionRef()).isNotEqualTo(second.getTransactionRef());
    }

    // ─────────────────────────────────────────────
    // Balance verification during transfer
    // ─────────────────────────────────────────────

    @Test
    void shouldDeductExactAmountFromSender_andCreditReceiver() {
        UserAccountResponse userA = walletService.createUserAndAccount(
                new CreateUserRequest("exact_a@example.com"));
        UserAccountResponse userB = walletService.createUserAndAccount(
                new CreateUserRequest("exact_b@example.com"));

        seedBalance(userA.getAccountNumber(), new BigDecimal("5000.00"));

        walletService.doTransfer(
                new TransferRequest(userA.getAccountNumber(), userB.getAccountNumber(), new BigDecimal("1500.00")));

        // Sender must have exactly 3500 left
        assertThat(walletService.getAccountBalance(userA.getAccountNumber()).getBalance())
                .isEqualByComparingTo(new BigDecimal("3500.00"));

        // Receiver must have exactly 1500
        assertThat(walletService.getAccountBalance(userB.getAccountNumber()).getBalance())
                .isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void shouldSucceed_whenSenderBalanceExactlyEqualsTransferAmount() {
        UserAccountResponse userA = walletService.createUserAndAccount(
                new CreateUserRequest("exact_match_a@example.com"));
        UserAccountResponse userB = walletService.createUserAndAccount(
                new CreateUserRequest("exact_match_b@example.com"));

        seedBalance(userA.getAccountNumber(), new BigDecimal("200.00"));

        TransferResponse result = walletService.doTransfer(
                new TransferRequest(userA.getAccountNumber(), userB.getAccountNumber(), new BigDecimal("200.00")));

        // Sender balance should be exactly zero
        assertThat(result.getNewFromBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(walletService.getAccountBalance(userA.getAccountNumber()).getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldThrowInsufficientFunds_whenAmountExceedsBalanceByOneCent() {
        UserAccountResponse sender = walletService.createUserAndAccount(
                new CreateUserRequest("one_cent_a@example.com"));
        UserAccountResponse receiver = walletService.createUserAndAccount(
                new CreateUserRequest("one_cent_b@example.com"));

        seedBalance(sender.getAccountNumber(), new BigDecimal("100.00"));

        // Attempt to send 100.01 — one cent over the balance
        assertThatThrownBy(() -> walletService.doTransfer(
                new TransferRequest(sender.getAccountNumber(), receiver.getAccountNumber(), new BigDecimal("100.01"))))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("100.00");  // message shows the available balance

        // Balance must be unchanged after the failed attempt
        assertThat(walletService.getAccountBalance(sender.getAccountNumber()).getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldAccumulateBalance_acrossMultipleInboundTransfers() {
        UserAccountResponse sender1 = walletService.createUserAndAccount(
                new CreateUserRequest("multi_s1@example.com"));
        UserAccountResponse sender2 = walletService.createUserAndAccount(
                new CreateUserRequest("multi_s2@example.com"));
        UserAccountResponse receiver = walletService.createUserAndAccount(
                new CreateUserRequest("multi_recv@example.com"));

        seedBalance(sender1.getAccountNumber(), new BigDecimal("1000.00"));
        seedBalance(sender2.getAccountNumber(), new BigDecimal("1000.00"));

        walletService.doTransfer(
                new TransferRequest(sender1.getAccountNumber(), receiver.getAccountNumber(), new BigDecimal("300.00")));
        walletService.doTransfer(
                new TransferRequest(sender2.getAccountNumber(), receiver.getAccountNumber(), new BigDecimal("200.00")));

        // Receiver should have exactly 500 from both inbound transfers
        assertThat(walletService.getAccountBalance(receiver.getAccountNumber()).getBalance())
                .isEqualByComparingTo(new BigDecimal("500.00"));

        // Both transaction records should be in receiver's history
        List<TransactionRecordResponse> history = walletService.getTransactionHistory(receiver.getAccountNumber());
        assertThat(history).hasSize(2);
    }

    @Test
    void shouldRecordTransactionWithCorrectBalanceAfterDebit() {
        UserAccountResponse userA = walletService.createUserAndAccount(
                new CreateUserRequest("audit_a@example.com"));
        UserAccountResponse userB = walletService.createUserAndAccount(
                new CreateUserRequest("audit_b@example.com"));

        seedBalance(userA.getAccountNumber(), new BigDecimal("1000.00"));

        walletService.doTransfer(
                new TransferRequest(userA.getAccountNumber(), userB.getAccountNumber(), new BigDecimal("350.00")));

        List<TransactionRecordResponse> history = walletService.getTransactionHistory(userA.getAccountNumber());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getBalanceAfterDebit()).isEqualByComparingTo(new BigDecimal("650.00"));
        assertThat(history.get(0).getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(history.get(0).getTransactionRef()).isNotBlank();
        assertThat(history.get(0).getCreatedAt()).isNotNull();
    }

    // ─────────────────────────────────────────────
    // Audit timestamps
    // ─────────────────────────────────────────────

    @Test
    void shouldPopulateCreatedAtAndUpdatedAt_onAccountCreation() {
        UserAccountResponse response = walletService.createUserAndAccount(
                new CreateUserRequest("ts_check@example.com"));

        assertThat(response.getCreatedAt()).isNotNull();
    }

    // ─────────────────────────────────────────────
    // Test helper
    // ─────────────────────────────────────────────

    private void seedBalance(String accountNumber, BigDecimal amount) {
        com.example.test.model.Account account = accountJpaRepository
                .findByAccountNumber(accountNumber).orElseThrow();
        account.setBalance(amount);
        accountJpaRepository.save(account);
    }
}
