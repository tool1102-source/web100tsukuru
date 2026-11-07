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
 * 投票券を入れた1回分。同じ人が同じお題に何回でも、何枚でも入れられる。
 * 無料の券から先に使い、どちらを何枚使ったかを残す（返すときに同じ種類で返すため）。
 */
@Entity
@Table(indexes = {
        @Index(name = "poll_vote_poll", columnList = "pollId"),
        @Index(name = "poll_vote_user", columnList = "userId")
})
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pollId;

    @Column(nullable = false)
    private Long optionId;

    @Column(nullable = false)
    private Long userId;

    // 入れた枚数（= freeUsed + paidUsed）
    @Column(nullable = false)
    private long tickets;

    @Column(nullable = false)
    private long freeUsed;

    @Column(nullable = false)
    private long paidUsed;

    // 1位にならなかったので返したか
    @Column(nullable = false)
    private boolean refunded;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    public PollVote() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public PollVote(Long pollId, Long optionId, Long userId, long freeUsed, long paidUsed) {
        this.pollId = pollId;
        this.optionId = optionId;
        this.userId = userId;
        this.freeUsed = freeUsed;
        this.paidUsed = paidUsed;
        this.tickets = freeUsed + paidUsed;
    }

    public Long getId() {
        return id;
    }

    public Long getPollId() {
        return pollId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getTickets() {
        return tickets;
    }

    public long getFreeUsed() {
        return freeUsed;
    }

    public long getPaidUsed() {
        return paidUsed;
    }

    public boolean isRefunded() {
        return refunded;
    }

    public void markRefunded() {
        this.refunded = true;
    }
}
