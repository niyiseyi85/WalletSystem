package com.example.test.repository;

import com.example.test.model.WalletBalance;

import java.util.Optional;

public interface WalletBalanceRepository {

    Optional<WalletBalance> findByIdWithLock(Long id);

    WalletBalance save(WalletBalance walletBalance);
}
