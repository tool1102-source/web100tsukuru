package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 「ちいさな投票所」のフォロー1件（followerId の人が followeeId の人をフォローしている）。
 * 同じ組み合わせは1件だけ（二度押しても増えない）。
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "user_follow_pair", columnNames = {"followerId", "followeeId"}),
        indexes = @Index(name = "user_follow_followee", columnList = "followeeId"))
public class UserFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // フォローした人
    @Column(nullable = false)
    private Long followerId;

    // フォローされた人
    @Column(nullable = false)
    private Long followeeId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    public UserFollow() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public UserFollow(Long followerId, Long followeeId) {
        this.followerId = followerId;
        this.followeeId = followeeId;
    }

    public Long getId() {
        return id;
    }

    public Long getFollowerId() {
        return followerId;
    }

    public Long getFolloweeId() {
        return followeeId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
