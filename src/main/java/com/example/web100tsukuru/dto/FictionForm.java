package com.example.web100tsukuru.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// フォームから受け取るデータ構造
public class FictionForm {

    public static final int TITLE_MAX = 50;
    public static final int AUTHOR_MAX = 30;
    public static final int BODY_MAX = 20000;

    @NotBlank(message = "タイトルを入力してください。")
    @Size(max = TITLE_MAX, message = "タイトルは{max}文字以内で入力してください。")
    private String title;

    @NotBlank(message = "本文を入力してください。")
    @Size(max = BODY_MAX, message = "本文は{max}文字以内で入力してください。")
    private String body;

    @NotBlank(message = "投稿者名を入力してください。")
    @Size(max = AUTHOR_MAX, message = "投稿者名は{max}文字以内で入力してください。")
    private String authorName;

    /**
     * スパム対策のおとり欄。画面では見えないので、人は入力しない。
     * ここに何か入っていたら機械（ボット）による投稿とみなして保存しない。
     */
    private String website;

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

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public boolean isFilledByBot() {
        return website != null && !website.isBlank();
    }

    // 画面の入力欄の文字数上限（maxlength）に使う
    public int getTitleMax() {
        return TITLE_MAX;
    }

    public int getAuthorMax() {
        return AUTHOR_MAX;
    }

    public int getBodyMax() {
        return BODY_MAX;
    }
}
