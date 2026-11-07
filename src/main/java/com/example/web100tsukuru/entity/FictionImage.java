package com.example.web100tsukuru.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 投稿に付けた画像。本文の imageNo 番目の [IMG] の位置に入る。
 * 中身は縮めて JPEG にし直したもの（service/ImageProcessor）。
 */
@Entity
@Table(indexes = @Index(name = "fiction_image_fiction_no", columnList = "fictionId, imageNo", unique = true))
public class FictionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // どの投稿の画像か
    @Column(nullable = false)
    private Long fictionId;

    // 何枚目か（1 始まり）
    @Column(nullable = false)
    private int imageNo;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    // JPEG の中身（PostgreSQL のバイナリ型。テストの H2 も PostgreSQL 互換で受け付ける）
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    public FictionImage() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public FictionImage(Long fictionId, int imageNo, int width, int height, byte[] data) {
        this.fictionId = fictionId;
        this.imageNo = imageNo;
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public Long getFictionId() {
        return fictionId;
    }

    public int getImageNo() {
        return imageNo;
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
