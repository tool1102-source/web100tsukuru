package com.example.web100tsukuru.site;

import java.util.List;

/**
 * 「作ったwebサイト一覧」の1件。
 *
 * @param number      通し番号（No.001 など）
 * @param title       サイト名
 * @param description 一言説明
 * @param url         サイトのURL（このサイト内のページなら "/fiction" のように / から書く）
 * @param launched    公開した時期（例: "2025年11月"）
 * @param tags        サイトの特徴（2〜3個）
 * @param image       サムネイル画像（static/ からのパス）
 */
public record Website(int number, String title, String description, String url,
                      String launched, List<String> tags, String image) {

    public String label() {
        return String.format("No.%03d", number);
    }

    public String paddedNumber() {
        return String.format("%03d", number);
    }

    /** 別のサイトへのリンクか（新しいタブで開く） */
    public boolean external() {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /** 画面に出す URL（https:// や末尾の / を省いたもの） */
    public String displayUrl() {
        return external() ? url.replaceFirst("^https?://", "").replaceFirst("/$", "") : "web100tsukuru.com" + url;
    }
}
