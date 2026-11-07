package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.TicketLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketLedgerRepository extends JpaRepository<TicketLedger, Long> {

    // マイページに出す、最近の券の出入り
    List<TicketLedger> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
