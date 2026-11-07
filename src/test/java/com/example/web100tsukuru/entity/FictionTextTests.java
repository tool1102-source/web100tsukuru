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
    void 抜粋は改行をつぶして指定文字数で切る() {
        assertThat(FictionText.excerpt("あい\nうえ[IMG]お", 3)).isEqualTo("あい …");
        assertThat(FictionText.excerpt("短い", 80)).isEqualTo("短い");
        // 絵文字の途中で切らない
        assertThat(FictionText.excerpt("😀😀😀", 2)).isEqualTo("😀😀…");
    }
}
