package com.example.web100tsukuru.entity;

import com.example.web100tsukuru.Web100tsukuruApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * No.004「らくがきオークション」の絵1枚。
 * 描いた人（artistId）はずっと変わらず、持ち主は人（ownerUserId）か収集家（ownerNpc）のどちらか。
 * 絵の特徴（塗った広さ・細かさ・色ごとの割合）は保存のときにサーバーで測り、収集家の値付けに使う。
 */
@Entity
@Table(indexes = {
        @Index(name = "artwork_owner", columnList = "ownerUserId"),
        @Index(name = "artwork_artist", columnList = "artistId, createdAt"),
        @Index(name = "artwork_npc", columnList = "ownerNpc, relistAt"),
        @Index(name = "artwork_price", columnList = "lastPrice")
})
public class Artwork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long artistId;

    private Long ownerUserId;

    @Column(length = 16)
    private String ownerNpc;

    @Column(nullable = false, length = 40)
    private String title;

    // 公式の絵のときの原画（例：葛飾北斎『神奈川沖浪裏』）。ふつうの絵は null
    @Column(length = 100)
    private String credit;

    // 白以外で塗った広さ（0〜1）
    @Column(nullable = false)
    private double ink;

    // 色の境目の多さ（0〜1）。線が細かいほど大きい
    @Column(nullable = false)
    private double detail;

    // 使った色の数（塗った部分の2%以上を占める色）
    @Column(nullable = false)
    private int colors;

    // 色ごとの割合（塗った部分に対する割合。パレットの1〜12番をカンマ区切り）
    @Column(nullable = false, length = 120)
    private String shares;

    // 最後に売れた値段（まだ売れていなければ 0）
    @Column(nullable = false)
    private long lastPrice;

    @Column(nullable = false)
    private int saleCount;

    // いま出品中の競り（なければ null）
    private Long openLotId;

    // 収集家が持っているとき、次に競りに出す日時（出さないなら null）
    private LocalDateTime relistAt;

    // 収集家が出して売れなかった回数（3回で手放さなくなる）
    @Column(nullable = false)
    private int npcFails;

    @Column(nullable = false)
    private int reportCount;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(Web100tsukuruApplication.ZONE);

    @Version
    private long version;

    protected Artwork() {
    }

    public Artwork(Long artistId, String title, double ink, double detail, int colors, double[] shares) {
        this.artistId = artistId;
        this.ownerUserId = artistId;
        this.title = title;
        this.ink = ink;
        this.detail = detail;
        this.colors = colors;
        this.shares = joinShares(shares);
    }

    static String joinShares(double[] shares) {
        StringBuilder sb = new StringBuilder();
        for (int c = 1; c < shares.length; c++) {
            if (c > 1) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.3f", shares[c]));
        }
        return sb.toString();
    }

    /** 色ごとの割合（添字はパレットの番号。0 は白なので常に 0） */
    public double[] shareArray() {
        double[] result = new double[13];
        String[] parts = shares.split(",");
        for (int i = 0; i < parts.length && i < 12; i++) {
            result[i + 1] = Double.parseDouble(parts[i]);
        }
        return result;
    }

    public void credit(String credit) {
        this.credit = credit;
    }

    public String getCredit() {
        return credit;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(ownerUserId);
    }

    public boolean isOwnedByNpc() {
        return ownerNpc != null;
    }

    public boolean isOnSale() {
        return openLotId != null;
    }

    public void moveTo(Long userId, String npc) {
        this.ownerUserId = userId;
        this.ownerNpc = npc;
    }

    public void sold(long price) {
        this.lastPrice = price;
        this.saleCount++;
        this.openLotId = null;
        this.npcFails = 0;
    }

    public void unsold() {
        this.openLotId = null;
    }

    public void putOnSale(Long lotId) {
        this.openLotId = lotId;
        this.relistAt = null;
    }

    public void scheduleRelist(LocalDateTime at) {
        this.relistAt = at;
    }

    public void npcFailed() {
        this.npcFails++;
    }

    public void reported() {
        this.reportCount++;
    }

    public Long getId() {
        return id;
    }

    public Long getArtistId() {
        return artistId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getOwnerNpc() {
        return ownerNpc;
    }

    public String getTitle() {
        return title;
    }

    public double getInk() {
        return ink;
    }

    public double getDetail() {
        return detail;
    }

    public int getColors() {
        return colors;
    }

    public String getShares() {
        return shares;
    }

    public long getLastPrice() {
        return lastPrice;
    }

    public int getSaleCount() {
        return saleCount;
    }

    public Long getOpenLotId() {
        return openLotId;
    }

    public LocalDateTime getRelistAt() {
        return relistAt;
    }

    public int getNpcFails() {
        return npcFails;
    }

    public int getReportCount() {
        return reportCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
