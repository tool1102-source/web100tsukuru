package com.example.web100tsukuru.repository;

/**
 * No.003「ちいさな投票所」の DB の読み書き。
 * 各インターフェースは同じフォルダの別ファイルにある（AppUserRepository など）。
 * このファイルは、表示に要る値だけを取り出すための形（中身の大きい列は読まない）をまとめたもの。
 */
public final class PollRepositories {

    private PollRepositories() {
    }

    /** 選択肢ごとの券の合計 */
    public interface OptionTotal {
        Long getOptionId();

        Long getTickets();
    }

    /** お題ごとの券の合計 */
    public interface PollTotal {
        Long getPollId();

        Long getTickets();
    }

    /** 画像の中身を読まずに、表示に要る情報だけを取り出す */
    public interface PollImageInfo {
        Long getPollId();

        int getSlot();

        int getWidth();

        int getHeight();
    }
}
