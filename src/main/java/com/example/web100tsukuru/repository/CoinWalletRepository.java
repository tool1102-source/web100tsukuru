package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.CoinWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CoinWalletRepository extends JpaRepository<CoinWallet, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from CoinWallet w where w.userId = :userId")
    Optional<CoinWallet> lockById(@Param("userId") Long userId);
}
