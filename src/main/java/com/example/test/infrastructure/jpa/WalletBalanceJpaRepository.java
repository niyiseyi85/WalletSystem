package com.example.test.infrastructure.jpa;

import com.example.test.model.WalletBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletBalanceJpaRepository extends JpaRepository<WalletBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletBalance w WHERE w.id = :id")
    Optional<WalletBalance> findByIdWithLock(@Param("id") Long id);
}
