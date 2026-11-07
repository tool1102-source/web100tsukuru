package com.example.web100tsukuru.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * お題や報告に付けた画像。slot 0 はお題の画像、1〜3 は報告の画像。
 * 中身は縮めて JPEG にし直したもの（service/ImageProcessor）。
 */
@Entity
@Table(indexes = @Index(name = "poll_image_poll_slot", columnList = "pollId, slot", unique = true))
public class PollImage {

    public static final int QUESTION_SLOT = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pollId;

    @Column(nullable = false)
    private int slot;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    // JPEG の中身（PostgreSQL のバイナリ型。テストの H2 も PostgreSQL 互換で受け付ける）
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    public PollImage() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public PollImage(Long pollId, int slot, int width, int height, byte[] data) {
        this.pollId = pollId;
        this.slot = slot;
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public Long getPollId() {
        return pollId;
    }

    public int getSlot() {
        return slot;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getData() {
        return data;
    }
}
