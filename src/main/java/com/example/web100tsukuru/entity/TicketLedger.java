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

/**
 * 投票券の出入りの記録（1行が1回の増減）。
 * 有料の券を売るようになったら、売った枚数・使った枚数・残りの合計をここから出せるようにしておく
 * （前払いで売る券は、残りの合計額によって届け出が要るため）。
 */
@Entity
@Table(indexes = @Index(name = "ticket_ledger_user", columnList = "userId, createdAt"))
public class TicketLedger {

    /** 増減の理由 */
    public enum Reason {
        SIGNUP("はじめての投票券"),
        DAILY_BONUS("ログインボーナス"),
        VOTE("投票"),
        REFUND("1位にならなかったので返却"),
        DRAW("引き分けなので返却"),
        POLL_DELETED("お題が消されたので返却");

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

    // 無料の券の増減（減るときはマイナス）
    @Column(nullable = false)
    private long freeDelta;

    // 有料の券の増減（減るときはマイナス）
    @Column(nullable = false)
    private long paidDelta;

    @Column(nullable = false, length = 20)
    private String reason;

    // 投票や返却なら、どのお題か
    private Long pollId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    public TicketLedger() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public TicketLedger(Long userId, long freeDelta, long paidDelta, Reason reason, Long pollId) {
        this.userId = userId;
        this.freeDelta = freeDelta;
        this.paidDelta = paidDelta;
        this.reason = reason.name();
        this.pollId = pollId;
    }

    public long getFreeDelta() {
        return freeDelta;
    }

    public long getPaidDelta() {
        return paidDelta;
    }

    public long getTotalDelta() {
        return freeDelta + paidDelta;
    }

    public Reason getReasonType() {
        return Reason.valueOf(reason);
    }

    public Long getPollId() {
        return pollId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
