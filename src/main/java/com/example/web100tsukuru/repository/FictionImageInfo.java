package com.example.web100tsukuru.repository;

/** 画像の中身（data）を読まずに、表示に要る情報だけを取り出す */
public interface FictionImageInfo {

    Long getFictionId();

    int getImageNo();

    int getWidth();

    int getHeight();
}
