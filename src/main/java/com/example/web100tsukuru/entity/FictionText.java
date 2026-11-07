package com.example.web100tsukuru.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 本文の [IMG] 目印まわりの文字列処理 */
public final class FictionText {

    public static final String PLACEHOLDER = "[IMG]";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(Pattern.quote(PLACEHOLDER));

    private FictionText() {
    }

    /** 本文に含まれる [IMG] の数 */
    public static int countPlaceholders(String body) {
        if (body == null) {
            return 0;
        }
        return (int) PLACEHOLDER_PATTERN.matcher(body).results().count();
    }

    /**
     * 読者向けの本文。[IMG] の目印は表示しない。
     * 目印を消したことでできた余分な空行もまとめる。
     */
    public static String readerText(String body) {
        if (body == null) {
            return "";
        }
        return PLACEHOLDER_PATTERN.matcher(body).replaceAll("")
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    /**
     * 画像を imageCount 枚入れるために、本文を先頭から imageCount 個の [IMG] の位置で区切る。
     * 返す文章はいつも imageCount + 1 個（i 枚目の画像は i 番目と i+1 番目の文章の間に入る）。
     * [IMG] が画像より少なければ、足りない分は空の文章にする（画像は本文の最後に並ぶ）。
     * 画像より多い [IMG] は、readerText と同じく消す。
     */
    public static List<String> splitForImages(String body, int imageCount) {
        String text = body == null ? "" : body;
        List<String> parts = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        int start = 0;
        while (parts.size() < imageCount && matcher.find()) {
            parts.add(readerText(text.substring(start, matcher.start())));
            start = matcher.end();
        }
        parts.add(readerText(text.substring(start)));
        while (parts.size() < imageCount + 1) {
            parts.add("");
        }
        return parts;
    }

    /** 改行をつぶして、先頭から maxLength 文字までの抜粋を作る */
    public static String excerpt(String body, int maxLength) {
        String flat = readerText(body).replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= maxLength) {
            return flat;
        }
        int end = flat.offsetByCodePoints(0, maxLength);
        return flat.substring(0, end) + "…";
    }
}
