package com.example.web100tsukuru.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 競りの入札1回（人か収集家のどちらか。auto は上限までの自動入札で上がった分） */
@Entity
@Table(indexes = @Index(name = "auction_bid_lot", columnList = "lotId, id"))
public class AuctionBid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long lotId;

    private Long userId;

    @Column(length = 16)
    private String npc;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private LocalDateTime bidAt;

    @Column(nullable = false)
    private boolean auto;

    // 収集家のひとこと
    @Column(length = 60)
    private String line;

    protected AuctionBid() {
    }

    public AuctionBid(Long lotId, Long userId, String npc, long amount, LocalDateTime bidAt, boolean auto, String line) {
        this.lotId = lotId;
        this.userId = userId;
        this.npc = npc;
        this.amount = amount;
        this.bidAt = bidAt;
        this.auto = auto;
        this.line = line;
    }

    public Long getId() {
        return id;
    }

    public Long getLotId() {
        return lotId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNpc() {
        return npc;
    }

    public long getAmount() {
        return amount;
    }

    public LocalDateTime getBidAt() {
        return bidAt;
    }

    public boolean isAuto() {
        return auto;
    }

    public String getLine() {
        return line;
    }
}
