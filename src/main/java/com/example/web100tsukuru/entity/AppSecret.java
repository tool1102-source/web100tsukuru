package com.example.web100tsukuru.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * サーバーだけが知っている鍵（ログイン状態の Cookie に署名するため）。
 * はじめて要るときにランダムに作って DB に保存する。Render の環境変数に入れなくてよいように。
 */
@Entity
public class AppSecret {

    @Id
    @Column(length = 40)
    private String name;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] secret;

    public AppSecret() {
        // デフォルトコンストラクタ（JPAの要件）
    }

    public AppSecret(String name, byte[] secret) {
        this.name = name;
        this.secret = secret;
    }

    public byte[] getSecret() {
        return secret;
    }
}
