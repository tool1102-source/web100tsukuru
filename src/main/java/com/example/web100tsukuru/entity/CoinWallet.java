package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 「らくがきオークション」のコインの残り（アカウントは投票所と共通、コインは別） */
@Entity
public class CoinWallet implements Persistable<Long> {

    // アカウントの番号と同じ
    @Id
    private Long userId;

    @Column(nullable = false)
    private long coins;

    private LocalDate lastBonusDate;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    @Version
    private long version;

    // 番号は自分で決める（アカウントと同じ）ので、新しく作ったものかどうかを自分で持つ
    // （同時に2つの画面から作られたとき、上書きせずに重複の誤りにするため）
    @Transient
    private boolean fresh = true;

    protected CoinWallet() {
    }

    public CoinWallet(Long userId, long coins, LocalDate today) {
        this.userId = userId;
        this.coins = coins;
        this.lastBonusDate = today;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        this.fresh = false;
    }

    @Override
    public Long getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    public void add(long delta) {
        this.coins += delta;
    }

    public void bonusGiven(LocalDate today) {
        this.lastBonusDate = today;
    }

    public Long getUserId() {
        return userId;
    }

    public long getCoins() {
        return coins;
    }

    public LocalDate getLastBonusDate() {
        return lastBonusDate;
    }
}
