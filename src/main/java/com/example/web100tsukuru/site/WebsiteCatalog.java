package com.example.web100tsukuru.site;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * トップページに並べる「作ったwebサイト一覧」。
 * 新しいサイトを作ったら、ここに1行足すだけでトップページと進み具合に反映される。
 */
@Component
public class WebsiteCatalog {

    /** 目標の数 */
    public static final int GOAL = 100;

    private static final List<Website> WEBSITES = List.of(
            new Website(1, "開発者向け変換ツールまとめ",
                    "JSON・CSV・Epoch Time・PDF など、開発やデータ分析で使う変換ツールを無料で使えます。",
                    "https://spring-date-converter.onrender.com"),
            new Website(2, "フィクションブログ",
                    "「やってみた」風の架空の体験談を、だれでも投稿して読めるブログです。",
                    "/fiction")
    );

    /** 新しい順 */
    public List<Website> newestFirst() {
        return WEBSITES.stream().sorted(Comparator.comparingInt(Website::number).reversed()).toList();
    }

    public int count() {
        return WEBSITES.size();
    }

    public int goal() {
        return GOAL;
    }

    /** 進み具合（0〜100 の %） */
    public int progressPercent() {
        return Math.min(100, count() * 100 / GOAL);
    }
}
