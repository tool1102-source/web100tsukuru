package com.example.web100tsukuru.entity;

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
     * 読者向けの本文。画像アップロードはまだ無いので、[IMG] の目印は表示しない。
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
