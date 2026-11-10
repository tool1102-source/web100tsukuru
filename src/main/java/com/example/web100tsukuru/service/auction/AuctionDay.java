package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.service.auction.ArtCollectors.ArtCollector;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * その日の競り場のようす：来場している収集家（8人のうち5人）と、流行の色。
 * 日付だけで決まるので、明日のようすも前もって見せられる（「明日は青が流行るから、いま青い絵を買っておく」ができる）。
 */
public final class AuctionDay {

    public static final int ATTENDEES = 5;

    /** 流行の色。その色の割合が塗った部分の4割あれば、だれの付け値も1.5倍になる */
    public record Trend(String key, String label, int[] colors, String hex) {
    }

    public static final List<Trend> TRENDS = List.of(
            new Trend("red", "赤と桃色", new int[]{3, 10}, "#e23b3b"),
            new Trend("warm", "橙と黄色", new int[]{4, 5, 12}, "#f28c28"),
            new Trend("green", "緑と茶色", new int[]{6, 11}, "#3fa34d"),
            new Trend("blue", "青と水色", new int[]{7, 8}, "#2f5fd0"),
            new Trend("purple", "紫", new int[]{9}, "#8b4fc4"),
            new Trend("mono", "黒と灰色", new int[]{1, 2}, "#222222")
    );

    public record Day(LocalDate date, List<ArtCollector> attendees, Trend trend) {

        public boolean attends(String key) {
            return attendees.stream().anyMatch(c -> c.key().equals(key));
        }
    }

    private AuctionDay() {
    }

    public static Day of(LocalDate date) {
        long d = date.toEpochDay();
        List<ArtCollector> attendees = IntStream.range(0, ArtCollectors.ALL.size()).boxed()
                .sorted(Comparator.comparingLong(i -> mix(d * 31 + i)))
                .limit(ATTENDEES)
                .sorted()
                .map(ArtCollectors.ALL::get)
                .toList();
        Trend trend = TRENDS.get((int) Math.floorMod(mix(d * 7 + 3), (long) TRENDS.size()));
        return new Day(date, attendees, trend);
    }

    /** 数を混ぜてばらばらにする（同じ数からは必ず同じ値） */
    static long mix(long x) {
        long z = x + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
