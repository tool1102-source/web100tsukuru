package com.example.web100tsukuru.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FictionTextTests {

    @Test
    void IMGの数を数える() {
        assertThat(FictionText.countPlaceholders("a[IMG]b[IMG]")).isEqualTo(2);
        assertThat(FictionText.countPlaceholders("なし")).isZero();
        assertThat(FictionText.countPlaceholders(null)).isZero();
    }

    @Test
    void 読者向けの本文からIMGと余分な空行を消す() {
        assertThat(FictionText.readerText("一行目\r\n[IMG]\r\n\r\n\r\n二行目[IMG]"))
                .isEqualTo("一行目\n\n二行目");
    }

    @Test
    void 画像の枚数ぶんだけIMGの位置で区切る() {
        // 画像2枚・[IMG]2つ → 3つの文章の間に入る
        assertThat(FictionText.splitForImages("前\n[IMG]\n中[IMG]後", 2)).containsExactly("前", "中", "後");
        // 画像1枚・[IMG]2つ → 1つ目で区切り、2つ目は消す
        assertThat(FictionText.splitForImages("前[IMG]中[IMG]後", 1)).containsExactly("前", "中後");
        // 画像3枚・[IMG]1つ → 足りない分は最後に並ぶ
        assertThat(FictionText.splitForImages("前[IMG]後", 3)).containsExactly("前", "後", "", "");
        // 画像なし → 今までの読者向けの本文と同じ
        assertThat(FictionText.splitForImages("一行目[IMG]\n二行目", 0)).containsExactly("一行目\n二行目");
    }

    @Test
    void 抜粋は改行をつぶして指定文字数で切る() {
        assertThat(FictionText.excerpt("あい\nうえ[IMG]お", 3)).isEqualTo("あい …");
        assertThat(FictionText.excerpt("短い", 80)).isEqualTo("短い");
        // 絵文字の途中で切らない
        assertThat(FictionText.excerpt("😀😀😀", 2)).isEqualTo("😀😀…");
    }
}
