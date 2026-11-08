package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.service.Avatars;
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

    // 投稿日時（日本時間）
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

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

    // ==========================================================
    // 表示用（DBには保存しない）
    // ==========================================================

    /** 読者向けの本文（[IMG] の目印を取り除いたもの） */
    public String getReaderBody() {
        return FictionText.readerText(body);
    }

    /** 一覧や検索結果に出す短い抜粋 */
    public String getExcerpt() {
        return FictionText.excerpt(body, 80);
    }

    /** 投稿者の丸いアイコンに出す、名前の1文字目 */
    public String getAuthorInitial() {
        return Avatars.initial(authorName);
    }

    /** 投稿者の丸いアイコンの色の番号 */
    public int getAuthorColor() {
        return Avatars.color(authorName);
    }

    /** 画像のない投稿の、サムネイルの色の番号（投稿ごとに変える） */
    public int getThumbColor() {
        return id == null ? 0 : (int) Math.floorMod(id, (long) Avatars.COLORS);
    }

    /** 投稿から3日以内なら「NEW」を付ける */
    public boolean isRecent() {
        return createdAt != null && createdAt.isAfter(LocalDateTime.now(Web100tsukuruApplication.ZONE).minusDays(3));
    }
}
