package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 競り1回分（絵1枚を、開始価格・時間を決めて出したもの）。
 * いちばん高い人（top〜）が人なら、上限額（topMax）まで自動で競り上がる。
 * 収集家がいつ・いくらまで出すかは出品したときに決め（plan）、だれかがページを開いたときに時刻順に進める
 * （決まった時刻に動かす仕組みを使わないのは、無料の DB を定期的に起こさないため）。
 */
@Entity
@Table(indexes = {
        @Index(name = "lot_status_end", columnList = "status, endAt"),
        @Index(name = "lot_artwork", columnList = "artworkId"),
        @Index(name = "lot_top_user", columnList = "topUserId, status")
})
public class AuctionLot {

    public enum Status { OPEN, SOLD, UNSOLD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long artworkId;

    private Long sellerUserId;

    @Column(length = 16)
    private String sellerNpc;

    @Column(nullable = false)
    private long startPrice;

    // いまの値段（入札がなければ開始価格）
    @Column(nullable = false)
    private long price;

    private Long topUserId;

    @Column(length = 16)
    private String topNpc;

    // いちばん高い人の上限額（人のときだけ）
    @Column(nullable = false)
    private long topMax;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    // 最後に値段が動いた時刻（収集家の次の動きを決めるのに使う）
    @Column(nullable = false)
    private LocalDateTime lastBidAt;

    @Column(nullable = false)
    private int durationMinutes;

    // 終わる直前の入札で延びる長さ
    @Column(nullable = false)
    private int extendSeconds;

    // 収集家の予定（"key:上限:来る時刻のミリ秒;..."）
    @Column(nullable = false, length = 400)
    private String plan;

    @Column(nullable = false)
    private long seed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private int bidCount;

    private LocalDateTime settledAt;

    @Version
    private long version;

    protected AuctionLot() {
    }

    public AuctionLot(Long artworkId, Long sellerUserId, String sellerNpc, long startPrice, LocalDateTime startAt,
                      int durationMinutes, int extendSeconds, String plan, long seed) {
        this.artworkId = artworkId;
        this.sellerUserId = sellerUserId;
        this.sellerNpc = sellerNpc;
        this.startPrice = startPrice;
        this.price = startPrice;
        this.startAt = startAt;
        this.endAt = startAt.plusMinutes(durationMinutes);
        this.lastBidAt = startAt;
        this.durationMinutes = durationMinutes;
        this.extendSeconds = extendSeconds;
        this.plan = plan;
        this.seed = seed;
    }

    public boolean hasTop() {
        return topUserId != null || topNpc != null;
    }

    public boolean isTopUser(Long userId) {
        return userId != null && userId.equals(topUserId);
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    /** 受付中で、まだ終わりの時刻になっていない */
    public boolean isLive() {
        return status == Status.OPEN && LocalDateTime.now(Web100tsukuruApplication.ZONE).isBefore(endAt);
    }

    public boolean isSellerUser(Long userId) {
        return userId != null && userId.equals(sellerUserId);
    }

    /** いちばん高い人が入れ替わった（終わる直前なら延長） */
    public void bidBy(Long userId, String npc, long newPrice, long max, LocalDateTime at) {
        this.topUserId = userId;
        this.topNpc = npc;
        this.price = newPrice;
        this.topMax = max;
        touch(at);
    }

    /** いちばん高い人はそのままで、値段だけ上がった */
    public void priceUp(long newPrice, LocalDateTime at) {
        this.price = newPrice;
        touch(at);
    }

    public void raiseMax(long max) {
        this.topMax = max;
    }

    private void touch(LocalDateTime at) {
        this.lastBidAt = at;
        this.bidCount++;
        LocalDateTime minEnd = at.plusSeconds(extendSeconds);
        if (endAt.isBefore(minEnd)) {
            endAt = minEnd;
        }
    }

    public void settle(Status result, LocalDateTime at) {
        this.status = result;
        this.settledAt = at;
    }

    /** 終わるまでの残り（終わっていれば 0） */
    public Duration remaining() {
        Duration d = Duration.between(LocalDateTime.now(Web100tsukuruApplication.ZONE), endAt);
        return d.isNegative() ? Duration.ZERO : d;
    }

    /** 一覧に出す残り時間（「あと2分」「あと5時間」） */
    public String getTimeLeft() {
        long s = remaining().getSeconds();
        if (s <= 0) {
            return "まもなく終了";
        }
        if (s < 60) {
            return "あと" + s + "秒";
        }
        if (s < 3600) {
            return "あと" + (s / 60) + "分";
        }
        if (s < 86400) {
            return "あと" + (s / 3600) + "時間";
        }
        return "あと" + (s / 86400) + "日";
    }

    public Long getId() {
        return id;
    }

    public Long getArtworkId() {
        return artworkId;
    }

    public Long getSellerUserId() {
        return sellerUserId;
    }

    public String getSellerNpc() {
        return sellerNpc;
    }

    public long getStartPrice() {
        return startPrice;
    }

    public long getPrice() {
        return price;
    }

    public Long getTopUserId() {
        return topUserId;
    }

    public String getTopNpc() {
        return topNpc;
    }

    public long getTopMax() {
        return topMax;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public LocalDateTime getLastBidAt() {
        return lastBidAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getExtendSeconds() {
        return extendSeconds;
    }

    public String getPlan() {
        return plan;
    }

    public long getSeed() {
        return seed;
    }

    public Status getStatus() {
        return status;
    }

    public int getBidCount() {
        return bidCount;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }
}
