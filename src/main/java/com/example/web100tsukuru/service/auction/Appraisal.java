package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.entity.Artwork;
import com.example.web100tsukuru.service.auction.ArtCollectors.ArtCollector;
import com.example.web100tsukuru.service.auction.AuctionDay.Trend;
import com.example.web100tsukuru.service.auction.Drawing.Features;

import java.util.Random;

/**
 * 収集家の付け値。数字は経済を試しに回して決めた（はじめて出した絵は真ん中で360コインほど、
 * 収集家どうしの転売を続けても、値段は初値の2倍前後で落ち着く）。
 *
 * 付け値 = 絵のでき × 好み × 財力 × 流行 × 作者の評判 × 来歴 × ゆらぎ
 * 前に売れたことのある絵は、付け値の4分の1を前の値段に寄せる（急に値崩れしないように）。
 */
public final class Appraisal {

    // 好みがこれより低い絵には値を付けない
    public static final double MIN_TASTE = 0.8;
    // 「この収集家が好みそう」と画面に出す好みの下限
    public static final double LIKES = 1.2;

    private Appraisal() {
    }

    private static double sat(double x) {
        return Math.max(0, Math.min(1, x));
    }

    public static Features features(Artwork a) {
        return new Features(a.getInk(), a.getDetail(), a.getColors(), a.shareArray());
    }

    /** 絵のでき（40〜300）：塗った広さ・細かさ・色の数 */
    public static double base(Features f) {
        double q = 0.35 * sat(f.ink() / 0.3) + 0.35 * sat(f.detail() / 0.08) + 0.3 * sat((f.colors() - 1) / 5.0);
        return 40 + 260 * q;
    }

    /** 流行の色の倍率（1〜1.5） */
    public static double trendBonus(Features f, Trend trend) {
        return 1 + 0.5 * sat(f.shareOf(trend.colors()) / 0.4);
    }

    /** 作者の評判の倍率（その作者の絵が売れた回数が増えるほど上がる。10回で1.24倍、100回で1.46倍） */
    public static double reputation(long artistSales) {
        return 1 + 0.1 * Math.log1p(Math.max(0, artistSales));
    }

    /** 付け値（好みに合わなければ 0） */
    public static long value(ArtCollector c, Features f, Trend trend, long artistSales, int saleCount, long lastPrice, Random random) {
        double taste = c.tasteFor(f);
        if (taste < MIN_TASTE) {
            return 0;
        }
        double provenance = 1 + 0.04 * Math.min(saleCount, 5);
        double v = base(f) * taste * c.wealth() * trendBonus(f, trend) * reputation(artistSales) * provenance
                * Math.exp(0.2 * random.nextGaussian());
        if (lastPrice > 0) {
            v = 0.75 * v + 0.25 * lastPrice;
        }
        return Math.max(1, Math.round(v));
    }
}
