package com.example.web100tsukuru.service;

/**
 * 「ちいさな投票所」で、写真の代わりに出す丸いアイコン（名前の1文字目と色）。
 * 色は名前から決めるので、同じ人はいつも同じ色になる。
 */
public final class Avatars {

    /** 色の数（style.css の .avatar-0〜.avatar-5） */
    public static final int COLORS = 6;

    private Avatars() {
    }

    /** 名前の1文字目（絵文字などの2つ分の文字も、途中で切らない） */
    public static String initial(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String s = name.strip();
        return s.substring(0, s.offsetByCodePoints(0, 1));
    }

    /** 0〜5 の色の番号 */
    public static int color(String name) {
        return name == null ? 0 : Math.floorMod(name.hashCode(), COLORS);
    }
}
