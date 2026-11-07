package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.service.Avatars;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 「ちいさな投票所」の利用者。ログインは ID とパスワード（パスワードは BCrypt にして保存）か、Google。
 * Google で登録した人にも、ログイン ID とパスワードの欄は埋めておく（表の作りを変えずに済むように）。
 * そのときのパスワードの欄は BCrypt の形ではないので、パスワードではログインできない。
 * 投票券の残りは無料と有料に分けて持ち、増減はすべて TicketLedger にも残す。
 * （「user」は PostgreSQL の予約語なので、表の名前は app_user）
 */
@Entity
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ログインに使う ID（英小文字・数字・_ の3〜20文字）
    @Column(nullable = false, unique = true, length = 20)
    private String loginId;

    // 画面に出す名前
    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    // 「Google でログイン」した人の Google アカウントの番号（sub）。ID とパスワードで登録した人は空
    @Column(unique = true, length = 64)
    private String googleSub;

    @Column(nullable = false)
    private long freeTickets;

    @Column(nullable = false)
    private long paidTickets;

    // 最後にログインボーナスを渡した日（日本時間）
    private LocalDate lastBonusDate;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    // 同時に券を使ったときに、残りの数を取り違えないようにする
    @Version
    private long version;

    public AppUser() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public AppUser(String loginId, String displayName, String passwordHash) {
        this.loginId = loginId;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public void setGoogleSub(String googleSub) {
        this.googleSub = googleSub;
    }

    public long getFreeTickets() {
        return freeTickets;
    }

    public long getPaidTickets() {
        return paidTickets;
    }

    public long getTotalTickets() {
        return freeTickets + paidTickets;
    }

    public LocalDate getLastBonusDate() {
        return lastBonusDate;
    }

    public void setLastBonusDate(LocalDate lastBonusDate) {
        this.lastBonusDate = lastBonusDate;
    }

    /** 丸いアイコンに出す、表示名の1文字目（DB には保存しない） */
    public String getInitial() {
        return Avatars.initial(displayName);
    }

    /** 丸いアイコンの色の番号（DB には保存しない） */
    public int getAvatarColor() {
        return Avatars.color(displayName);
    }

    /** 券を増やす・減らす（減らしすぎないかは呼ぶ側で確かめる） */
    public void addTickets(long free, long paid) {
        this.freeTickets += free;
        this.paidTickets += paid;
    }
}
