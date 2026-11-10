package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.service.auction.Drawing.Features;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * 競りに来る8人の収集家（ゲームの登場人物で、実在の人ではない）。
 * それぞれ好みの絵があり、好み（taste）が 0.8 以上の絵にだけ値を付ける。好みは 0.4〜2.0。
 * 財力（wealth）は付け値の倍率。
 */
public final class ArtCollectors {

    public record ArtCollector(String key, String name, String taste, String color, double wealth,
                               ToDoubleFunction<Features> tasteFn,
                               List<String> openLines, List<String> raiseLines, List<String> winLines) {

        public double tasteFor(Features f) {
            return tasteFn.applyAsDouble(f);
        }

        public String initial() {
            return name.substring(0, 1);
        }

        /** 財力の目安（★1〜5） */
        public int stars() {
            return (int) Math.max(1, Math.min(5, Math.round(wealth * 2.5)));
        }
    }

    // 好みを決めるときの、割合の上限の目安（0.5 = 塗った部分の半分がその色なら最大）
    private static double sat(double x) {
        return Math.max(0, Math.min(1, x));
    }

    public static final List<ArtCollector> ALL = List.of(
            new ArtCollector("akasaka", "赤坂伯爵", "赤・橙・桃色の絵に目がない、古い屋敷の主。", "#b83a3a", 1.5,
                    f -> 0.4 + 1.6 * sat(f.shareOf(3, 4, 10) / 0.5),
                    List.of("ほう、良い赤だ。", "わが屋敷の壁に合いそうだ。", "この色の熱さ、悪くない。"),
                    List.of("まだ譲らんよ。", "伯爵家の意地を見せよう。"),
                    List.of("屋敷の大広間に飾らせてもらう。", "良い買い物だった。")),
            new ArtCollector("aoyagi", "青柳夫人", "青・水色・紫の絵を集める、海辺の別荘の夫人。", "#2f5fd0", 1.3,
                    f -> 0.4 + 1.6 * sat(f.shareOf(7, 8, 9) / 0.5),
                    List.of("まあ、海の色ね。", "この青、別荘に飾りたいわ。", "涼しげで素敵。"),
                    List.of("わたくしも引けませんの。", "もう少しだけ。"),
                    List.of("うれしい。大切にしますわ。", "別荘の寝室に飾りましょう。")),
            new ArtCollector("morino", "森野さん", "緑と茶色、森の色が好きな植木屋さん。", "#3f8a4d", 0.9,
                    f -> 0.4 + 1.6 * sat(f.shareOf(6, 11) / 0.5),
                    List.of("お、いい緑だねえ。", "森のにおいがする絵だ。", "植木の横に置きたいな。"),
                    List.of("もうひと声いこう。", "ここは負けられないな。"),
                    List.of("店先に飾らせてもらうよ。", "いい買い物をした。")),
            new ArtCollector("kanenari", "金成社長", "色が多くてにぎやかな絵ほど高く買う、大金持ち。", "#c9a227", 2.0,
                    f -> 0.4 + 1.6 * sat((f.colors() - 1) / 6.0) * Math.sqrt(sat(f.ink() / 0.4)),
                    List.of("にぎやかでいいじゃないか！", "色が多い絵は縁起がいい。", "社長室に飾るぞ。"),
                    List.of("金ならある。", "まだまだ上げるぞ。"),
                    List.of("よし、社長室行きだ！", "わっはっは、いただいた。")),
            new ArtCollector("hosokawa", "細川教授", "線が細かく、描きこまれた絵を好む学者。", "#6b5b95", 1.1,
                    f -> 0.4 + 1.6 * sat(f.detail() / 0.1),
                    List.of("ふむ、線に迷いがない。", "描きこみが実に興味深い。", "研究室に置いておきたい。"),
                    List.of("この細かさは見逃せません。", "もう少し調べさせてください。"),
                    List.of("講義で紹介させてもらいます。", "よい資料が手に入りました。")),
            new ArtCollector("shirai", "白井翁", "余白が多く、色の少ない絵を愛するご隠居。", "#8c8c8c", 1.2,
                    f -> 0.4 + 1.6 * sat(1 - Math.max(0, f.ink() - 0.12) / 0.25) * sat(1 - (f.colors() - 2) / 5.0),
                    List.of("……余白が、よい。", "多くを描かぬのが粋というもの。", "静かな絵じゃのう。"),
                    List.of("まだ、まだ。", "年寄りもしぶといぞ。"),
                    List.of("床の間に掛けるとしよう。", "ありがたく頂戴する。")),
            new ArtCollector("sumida", "墨田画伯", "黒と灰色だけで描いた絵を集める画家。", "#333333", 1.3,
                    f -> 0.4 + 1.6 * sat((f.shareOf(1, 2) - 0.3) / 0.5),
                    List.of("黒の使い方がいい。", "色はいらない。これでいい。", "墨の濃淡を感じる。"),
                    List.of("この絵は渡さない。", "もう一筆、乗せよう。"),
                    List.of("アトリエに飾らせてもらう。", "良い手本になる。")),
            new ArtCollector("himari", "ひまりちゃん", "黄色・桃色・水色・橙の明るい絵が好きな小学生。おこづかいは少なめ。", "#e57aa8", 0.6,
                    f -> 0.4 + 1.6 * sat(f.shareOf(5, 10, 7, 4) / 0.5),
                    List.of("かわいい！", "これ、ほしい！", "おこづかいで買えるかな？"),
                    List.of("まけないもん！", "もうちょっとだけ！"),
                    List.of("やったー！部屋にかざる！", "だいじにするね！"))
    );

    private ArtCollectors() {
    }

    public static Optional<ArtCollector> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return ALL.stream().filter(c -> c.key().equals(key)).findFirst();
    }

    public static String nameOf(String key) {
        return find(key).map(ArtCollector::name).orElse("収集家");
    }
}
