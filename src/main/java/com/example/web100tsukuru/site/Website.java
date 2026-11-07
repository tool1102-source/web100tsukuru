package com.example.web100tsukuru.site;

/**
 * 「作ったwebサイト一覧」の1件。
 *
 * @param number      通し番号（No.001 など）
 * @param title       サイト名
 * @param description 一言説明
 * @param url         サイトのURL（このサイト内のページなら "/fiction" のように / から書く）
 */
public record Website(int number, String title, String description, String url) {

    public String label() {
        return String.format("No.%03d", number);
    }

    /** 別のサイトへのリンクか（新しいタブで開く） */
    public boolean external() {
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
