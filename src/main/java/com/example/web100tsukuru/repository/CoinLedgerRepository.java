package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.CoinLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoinLedgerRepository extends JpaRepository<CoinLedger, Long> {

    List<CoinLedger> findTop30ByUserIdOrderByIdDesc(Long userId);
}
