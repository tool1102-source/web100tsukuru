package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.AuctionBid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {

    List<AuctionBid> findByLotIdOrderByIdAsc(Long lotId);

    List<AuctionBid> findTop40ByLotIdOrderByIdDesc(Long lotId);

    void deleteByLotIdIn(Collection<Long> lotIds);
}
