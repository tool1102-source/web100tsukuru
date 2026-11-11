package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.Artwork;
import com.example.web100tsukuru.entity.ArtworkImage;
import com.example.web100tsukuru.entity.ArtworkReport;
import com.example.web100tsukuru.entity.AuctionLot;
import com.example.web100tsukuru.entity.CoinLedger.Reason;
import com.example.web100tsukuru.repository.ArtworkImageRepository;
import com.example.web100tsukuru.repository.ArtworkReportRepository;
import com.example.web100tsukuru.repository.ArtworkRepository;
import com.example.web100tsukuru.repository.AuctionBidRepository;
import com.example.web100tsukuru.repository.AuctionLotRepository;
import com.example.web100tsukuru.service.auction.AuctionEngine.Length;
import com.example.web100tsukuru.service.auction.Drawing.InvalidDrawingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 絵を描いて保存する・競りに出す・報告する・（管理画面から）消す */
@Service
public class ArtworkService {

    public static final int TITLE_MAX = 20;
    // 白紙に近い絵は出さない（塗った広さが 0.5% 未満）
    static final double MIN_INK = 0.005;

    public static class ArtworkException extends Exception {
        public ArtworkException(String message) {
            super(message);
        }
    }

    private final ArtworkRepository artworks;
    private final ArtworkImageRepository images;
    private final ArtworkReportRepository reports;
    private final AuctionLotRepository lots;
    private final AuctionBidRepository bids;
    private final AuctionEngine engine;
    private final CoinService coins;
    private final int dailyLimit;

    public ArtworkService(ArtworkRepository artworks, ArtworkImageRepository images, ArtworkReportRepository reports,
                          AuctionLotRepository lots, AuctionBidRepository bids, AuctionEngine engine, CoinService coins,
                          @Value("${app.auction.daily-drawings:5}") int dailyLimit) {
        this.artworks = artworks;
        this.images = images;
        this.reports = reports;
        this.lots = lots;
        this.bids = bids;
        this.engine = engine;
        this.coins = coins;
        this.dailyLimit = dailyLimit;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    /** 今日あと何枚描けるか */
    @Transactional(readOnly = true)
    public int remainingToday(Long userId) {
        LocalDateTime since = LocalDate.now(Web100tsukuruApplication.ZONE).atStartOfDay();
        return (int) Math.max(0, dailyLimit - artworks.countByArtistIdAndCreatedAtGreaterThanEqual(userId, since));
    }

    /** 描いた絵を保存する（持ち主は描いた人） */
    @Transactional
    public Artwork create(Long userId, String title, byte[] png) throws ArtworkException {
        String t = title == null ? "" : title.strip();
        if (t.isEmpty() || t.codePointCount(0, t.length()) > TITLE_MAX) {
            throw new ArtworkException("題名は1〜" + TITLE_MAX + "文字で付けてください。");
        }
        if (remainingToday(userId) <= 0) {
            throw new ArtworkException("1日に描けるのは" + dailyLimit + "枚までです。また明日描いてください。");
        }
        Drawing.Result drawing;
        try {
            drawing = Drawing.read(png);
        } catch (InvalidDrawingException e) {
            throw new ArtworkException(e.getMessage());
        }
        Drawing.Features f = drawing.features();
        if (f.ink() < MIN_INK) {
            throw new ArtworkException("ほとんど白紙です。もう少し描いてから出してください。");
        }
        Artwork artwork = artworks.save(new Artwork(userId, t, f.ink(), f.detail(), f.colors(), f.share()));
        images.save(new ArtworkImage(artwork.getId(), drawing.png()));
        return artwork;
    }

    /** 公式の絵を登録する（1日の枚数の制限なし。原画を書き添える） */
    @Transactional
    public Artwork createOfficial(Long officialId, String title, String credit, byte[] png) throws ArtworkException {
        Drawing.Result drawing;
        try {
            drawing = Drawing.read(png);
        } catch (InvalidDrawingException e) {
            throw new ArtworkException(e.getMessage());
        }
        Drawing.Features f = drawing.features();
        Artwork artwork = new Artwork(officialId, title, f.ink(), f.detail(), f.colors(), f.share());
        artwork.credit(credit);
        artwork = artworks.save(artwork);
        images.save(new ArtworkImage(artwork.getId(), drawing.png()));
        return artwork;
    }

    /** 持っている絵を競りに出す */
    @Transactional
    public AuctionLot sell(Long userId, Long artworkId, long startPrice, Length length) throws ArtworkException {
        Artwork artwork = artworks.lockById(artworkId).orElseThrow(() -> new ArtworkException("絵が見つかりません。"));
        if (!artwork.isOwnedBy(userId)) {
            throw new ArtworkException("自分が持っている絵だけ出品できます。");
        }
        if (artwork.isOnSale()) {
            throw new ArtworkException("この絵はもう出品中です。");
        }
        if (startPrice < AuctionEngine.MIN_START || startPrice > AuctionEngine.MAX_PRICE) {
            throw new ArtworkException("開始価格は" + AuctionEngine.MIN_START + "コイン以上にしてください。");
        }
        return engine.open(artwork, userId, null, startPrice, length, AuctionEngine.now());
    }

    @Transactional(readOnly = true)
    public Optional<Artwork> find(Long id) {
        return artworks.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> png(Long id) {
        return images.findById(id).map(ArtworkImage::getPng);
    }

    /** 報告する。はじめての報告なら true（絵の行を押さえるので、同じ人の報告が同時に来ても二重にならない） */
    @Transactional
    public boolean report(Long userId, Long artworkId) {
        Artwork artwork = artworks.lockById(artworkId).orElse(null);
        if (artwork == null || reports.existsByArtworkIdAndUserId(artworkId, userId)) {
            return false;
        }
        reports.saveAndFlush(new ArtworkReport(artworkId, userId));
        artwork.reported();
        return true;
    }

    /**
     * 管理画面から消す。出品中の競りは入札ごと取り消し（上限額で押さえていたコインは使えるように戻る）、
     * ほかの人から買って持っていた人には、買った値段を返す。
     */
    @Transactional
    public List<Artwork> deleteAll(List<Long> ids) {
        List<Artwork> deleted = new ArrayList<>();
        for (Long id : ids) {
            Artwork artwork = artworks.lockById(id).orElse(null);
            if (artwork == null) {
                continue;
            }
            List<Long> lotIds = lots.findByArtworkId(id).stream().map(AuctionLot::getId).toList();
            if (!lotIds.isEmpty()) {
                bids.deleteByLotIdIn(lotIds);
                lots.deleteAllById(lotIds);
            }
            Long owner = artwork.getOwnerUserId();
            if (owner != null && !owner.equals(artwork.getArtistId()) && artwork.getLastPrice() > 0) {
                coins.add(owner, artwork.getLastPrice(), Reason.WORK_DELETED, id);
            }
            reports.deleteByArtworkId(id);
            images.deleteById(id);
            artworks.delete(artwork);
            deleted.add(artwork);
        }
        return deleted;
    }
}
