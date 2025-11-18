package com.example.web100tsukuru.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// フォームから受け取るデータ構造
public class FictionForm {

    // タイトルは必須（空を許さない）
    @NotBlank(message = "タイトルは必須です。")
    @Size(max = 50, message = "タイトルは50文字以内で入力してください。")
    private String title;

    // 本文は必須
    @NotBlank(message = "本文は必須です。")
    private String body;

    // 投稿者名も必須
    @NotBlank(message = "投稿者名は必須です。")
    @Size(max = 100, message = "投稿者名は100文字以内で入力してください。")
    private String authorName;

    // ==========================================================
    // ゲッターとセッター
    // ==========================================================
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
}