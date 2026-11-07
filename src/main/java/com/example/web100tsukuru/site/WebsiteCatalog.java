package com.example.web100tsukuru.site;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * トップページに並べる「作ったwebサイト一覧」。
 * 新しいサイトを作ったら、ここに1件足すだけでトップページ・100マス・進み具合に反映される。
 * サムネイルは static/img/sites/ に 1200×750 くらいの画像を置く。
 */
@Component
public class WebsiteCatalog {

    /** 目標の数 */
    public static final int GOAL = 100;

    private static final List<Website> WEBSITES = List.of(
            new Website(1, "ConverterTools",
                    "JSON・CSV・Excel・日付・PDF・文字列の変換が、ブラウザだけで済むツール集。データはサーバーに送りません。",
                    "/tools/", "2025年11月",
                    List.of("変換ツール", "ブラウザで完結"), "/img/sites/001.jpg"),
            new Website(2, "フィクションブログ",
                    "「やってみた」風の架空の体験談を、だれでも投稿して読めるブログ。",
                    "/fiction", "2025年11月",
                    List.of("投稿型ブログ", "読みもの"), "/img/sites/002.jpg"),
            new Website(3, "ちいさな投票所",
                    "だれかの小さな選択を、投票券で決める投票所。券は何枚でも入れられて、1位にならなかった券は全部戻ってくる。出した人は1位のとおりにやって報告する。",
                    "/vote", "2026年11月",
                    List.of("投票", "アカウント制"), "/img/sites/003.jpg")
    );

    /** 100マスの1マス分 */
    public record Tile(int number, Website site, boolean next) {
        public String label() {
            return String.format("%03d", number);
        }
    }

    /** 新しい順 */
    public List<Website> newestFirst() {
        return WEBSITES.stream().sorted(Comparator.comparingInt(Website::number).reversed()).toList();
    }

    /** 1〜100 のマス。作ったサイトはリンク付き、次に作る番号には印を付ける */
    public List<Tile> tiles() {
        Map<Integer, Website> byNumber = WEBSITES.stream().collect(Collectors.toMap(Website::number, Function.identity()));
        int next = IntStream.rangeClosed(1, GOAL).filter(n -> !byNumber.containsKey(n)).findFirst().orElse(-1);
        return IntStream.rangeClosed(1, GOAL).mapToObj(n -> new Tile(n, byNumber.get(n), n == next)).toList();
    }

    public int count() {
        return WEBSITES.size();
    }

    public int goal() {
        return GOAL;
    }

    public int remaining() {
        return Math.max(0, GOAL - count());
    }

    /** 画面に出す数（例: "002"） */
    public String countLabel() {
        return String.format("%03d", count());
    }

    /** 進み具合（0〜100 の %） */
    public int progressPercent() {
        return Math.min(100, count() * 100 / GOAL);
    }
}
