package com.example.web100tsukuru.service.auction;

import java.util.List;

/**
 * 「らくがきオークション」で使える13色（0番の白は紙の色で、消しゴムも白で塗る）。
 * 描く画面（static/js/auction-draw.js）の色と同じ順・同じ値にしておく。
 */
public final class Palette {

    public record Color(int index, String name, int rgb) {
        public String hex() {
            return String.format("#%06x", rgb);
        }
    }

    public static final List<Color> COLORS = List.of(
            new Color(0, "白", 0xffffff),
            new Color(1, "黒", 0x222222),
            new Color(2, "灰色", 0x9a9a9a),
            new Color(3, "赤", 0xe23b3b),
            new Color(4, "橙", 0xf28c28),
            new Color(5, "黄色", 0xf7d038),
            new Color(6, "緑", 0x3fa34d),
            new Color(7, "水色", 0x4cc3e8),
            new Color(8, "青", 0x2f5fd0),
            new Color(9, "紫", 0x8b4fc4),
            new Color(10, "桃色", 0xf49ac1),
            new Color(11, "茶色", 0x8a5a3b),
            new Color(12, "肌色", 0xf6cfa8)
    );

    public static final int SIZE = COLORS.size();

    private Palette() {
    }

    /** いちばん近い色の番号（ブラウザがわずかに色を変えて保存しても、元の色に戻せるように） */
    public static int nearest(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (Color c : COLORS) {
            int dr = r - ((c.rgb() >> 16) & 0xff);
            int dg = g - ((c.rgb() >> 8) & 0xff);
            int db = b - (c.rgb() & 0xff);
            int dist = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (dist < bestDist) {
                bestDist = dist;
                best = c.index();
            }
        }
        return best;
    }
}
