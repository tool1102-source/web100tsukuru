package com.example.web100tsukuru.dto;

import com.example.web100tsukuru.repository.FictionImageInfo;

/** 記事の本文を上から並べるときの1かたまり（文章か画像のどちらか） */
public record ArticleBlock(String text, FictionImageInfo image) {

    public static ArticleBlock text(String text) {
        return new ArticleBlock(text, null);
    }

    public static ArticleBlock image(FictionImageInfo image) {
        return new ArticleBlock(null, image);
    }
}
