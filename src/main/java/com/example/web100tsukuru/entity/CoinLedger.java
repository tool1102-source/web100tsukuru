package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** コインの出入りの記録（1行が1回の増減） */
@Entity
@Table(indexes = @Index(name = "coin_ledger_user", columnList = "userId, createdAt"))
public class CoinLedger {

    public enum Reason {
        START("はじめての参加"),
        DAILY_BONUS("ログインボーナス"),
        BUY("落札"),
        SELL("売上"),
        ROYALTY("作者への還元"),
        WORK_DELETED("返却（絵が削除されたため）");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long delta;

    @Column(nullable = false, length = 20)
    private String reason;

    private Long artworkId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    protected CoinLedger() {
    }

    public CoinLedger(Long userId, long delta, Reason reason, Long artworkId) {
        this.userId = userId;
        this.delta = delta;
        this.reason = reason.name();
        this.artworkId = artworkId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getDelta() {
        return delta;
    }

    public String getReasonLabel() {
        try {
            return Reason.valueOf(reason).getLabel();
        } catch (IllegalArgumentException e) {
            return reason;
        }
    }

    public Long getArtworkId() {
        return artworkId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
