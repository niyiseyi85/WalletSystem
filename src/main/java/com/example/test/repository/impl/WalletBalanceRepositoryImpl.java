package com.example.test.repository.impl;

import com.example.test.infrastructure.jpa.WalletBalanceJpaRepository;
import com.example.test.model.WalletBalance;
import com.example.test.repository.WalletBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WalletBalanceRepositoryImpl implements WalletBalanceRepository {

    private final WalletBalanceJpaRepository walletBalanceJpaRepository;

    @Override
    public Optional<WalletBalance> findByIdWithLock(Long id) {
        return walletBalanceJpaRepository.findByIdWithLock(id);
    }

    @Override
    public WalletBalance save(WalletBalance walletBalance) {
        return walletBalanceJpaRepository.save(walletBalance);
    }
}
