package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * No.003「ちいさな投票所」のお題。
 * 出した人が自分の小さな選択（明日の昼ごはん、髪を切るか…）をみんなの投票にゆだね、
 * 締め切りのあと、1位になったとおりにやって報告する。
 * 投票は投票券を何枚でも入れられ、1位にならなかった選択肢に入れた券は締め切りで返す（service/PollService）。
 */
@Entity
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 出した人（AppUser）
    @Column(nullable = false)
    private Long ownerUserId;

    // お題（例：明日の昼ごはん、どっちにする？）
    @Column(nullable = false, length = 100)
    private String title;

    // くわしい説明（なくてもよい）
    @Column(columnDefinition = "TEXT")
    private String detail;

    // 出した日時（日本時間）
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    // 締め切り（日本時間）
    @Column(nullable = false)
    private LocalDateTime closesAt;

    // 締め切り後に、1位以外の券を返し終えた日時（二重に返さないための印）
    private LocalDateTime settledAt;

    // 「決まったとおりにやりました」の報告
    @Column(columnDefinition = "TEXT")
    private String reportText;

    private LocalDateTime reportedAt;

    public Poll() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(LocalDateTime closesAt) {
        this.closesAt = closesAt;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public void markSettled() {
        this.settledAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);
    }

    public String getReportText() {
        return reportText;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public void report(String text) {
        this.reportText = text;
        this.reportedAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);
    }

    // ==========================================================
    // 表示用（DBには保存しない）
    // ==========================================================

    /** まだ投票を受け付けているか */
    public boolean isOpen() {
        return LocalDateTime.now(Web100tsukuruApplication.ZONE).isBefore(closesAt);
    }

    public boolean isReported() {
        return reportedAt != null;
    }

    /** 締め切りまでの残り時間（例：「あと3時間」「あと2日」）。締め切っていたら空 */
    public String getTimeLeft() {
        long minutes = Duration.between(LocalDateTime.now(Web100tsukuruApplication.ZONE), closesAt).toMinutes();
        if (minutes <= 0) {
            return "";
        }
        if (minutes < 60) {
            return "あと" + minutes + "分";
        }
        if (minutes < 60 * 24) {
            return "あと" + (minutes / 60) + "時間";
        }
        return "あと" + (minutes / (60 * 24)) + "日";
    }
}
