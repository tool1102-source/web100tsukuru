package com.example.web100tsukuru.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

// このクラスがデータベースの「fiction」テーブルに対応することを宣言
@Entity
public class Fiction {

    // 主キー（テーブルの一意な識別子）
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ブログのタイトル (必須項目)
    @Column(nullable = false, length = 255)
    private String title;

    // ブログの本文（テキストは長い文字列を保存するため@Column(columnDefinition)を使用）
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // 投稿者名
    @Column(nullable = false, length = 100)
    private String authorName;

    // プレースホルダーの数
    @Column(nullable = false)
    private int placeholderCount;

    // 投稿日時
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ==========================================================
    // コンストラクタ（通常、引数なしと全引数が必要）
    // ==========================================================

    public Fiction() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    // ==========================================================
    // ゲッターとセッター（データの取得と設定）
    // ==========================================================

    public Long getId() {
        return id;
    }

    // セッターとゲッターはすべて自動生成できますが、ここでは省略して必要なもののみ記載
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
    
    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public int getPlaceholderCount() {
        return placeholderCount;
    }

    public void setPlaceholderCount(int placeholderCount) {
        this.placeholderCount = placeholderCount;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    // setCreatedAtは不要（自動設定のため）
}
