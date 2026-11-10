package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.Artwork;
import com.example.web100tsukuru.entity.AuctionBid;
import com.example.web100tsukuru.entity.AuctionLot;
import com.example.web100tsukuru.entity.AuctionLot.Status;
import com.example.web100tsukuru.entity.CoinLedger.Reason;
import com.example.web100tsukuru.repository.ArtworkRepository;
import com.example.web100tsukuru.repository.AuctionBidRepository;
import com.example.web100tsukuru.repository.AuctionLotRepository;
import com.example.web100tsukuru.repository.CoinWalletRepository;
import com.example.web100tsukuru.service.auction.ArtCollectors.ArtCollector;
import com.example.web100tsukuru.service.auction.AuctionDay.Day;
import com.example.web100tsukuru.service.auction.Drawing.Features;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 競りの進行。
 *
 * 収集家の動きは、出品したときに「だれが・いつ来て・いくらまで出すか」だけ決めておき（plan）、
 * だれかがページを開くたびに、前回から今までに起きたはずの入札を時刻順に再現する。
 * 同じ競りを同時に進めないよう、競りの行を押さえてから進める。
 *
 * 人の入札は「上限額」。いちばん高い人の上限までは、ほかの入札に合わせて自動で競り上がる。
 * 終わる直前に入札があると、終わりが延びる（3分の競りは30秒、1時間は2分、1日は5分）。
 * 売れたら、手数料5%を引いて出品した人へ。描いた人以外が売ったときは、さらに5%を描いた人へ渡す。
 */
@Service
public class AuctionEngine {

    /** 競りの長さ */
    public enum Length {
        MINUTES_3(3, 30, "3分"),
        HOUR_1(60, 120, "1時間"),
        DAY_1(1440, 300, "1日");

        private final int minutes;
        private final int extendSeconds;
        private final String label;

        Length(int minutes, int extendSeconds, String label) {
            this.minutes = minutes;
            this.extendSeconds = extendSeconds;
            this.label = label;
        }

        public int getMinutes() {
            return minutes;
        }

        public String getLabel() {
            return label;
        }

        public static Length of(String name) {
            for (Length l : values()) {
                if (l.name().equals(name)) {
                    return l;
                }
            }
            return MINUTES_3;
        }
    }

    public static final double FEE = 0.05;
    public static final double ROYALTY = 0.05;
    public static final long MIN_START = 10;
    public static final long MAX_PRICE = 10_000_000;
    // 収集家が出し直すときの値段（前の値段の1.2倍から、売れないたびに1割ずつ下げる）
    static final double NPC_MARKUP = 1.2;
    // 収集家が同時に出しておく競りの数
    static final int NPC_LIVE_LIMIT = 3;

    /** 入札の刻み（今の値段が高いほど大きい） */
    public static long increment(long price) {
        if (price < 300) {
            return 10;
        }
        if (price < 1000) {
            return 20;
        }
        if (price < 3000) {
            return 50;
        }
        if (price < 10000) {
            return 100;
        }
        return 500;
    }

    /** 次に入札できる最低額 */
    public static long minimumBid(AuctionLot lot) {
        return lot.hasTop() ? lot.getPrice() + increment(lot.getPrice()) : lot.getStartPrice();
    }

    /** 収集家の予定1人分（max = 出してよい上限、arriveMs = 競りが始まってから来るまで） */
    record PlanEntry(String key, long max, long arriveMs) {
    }

    public enum BidOutcome {
        TOP("いちばん高い値を付けました。"),
        RAISED("上限額を上げました。"),
        OUTBID("ほかの人の上限のほうが高かったため、まだ2番手です。もう少し上げてみてください。"),
        TOO_LOW("入札額が低すぎます。"),
        NOT_ENOUGH("使えるコインが足りません。"),
        OWN("自分が出品した絵には入札できません。"),
        ENDED("この競りは終わりました。");

        private final String message;

        BidOutcome(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public boolean ok() {
            return this == TOP || this == RAISED || this == OUTBID;
        }
    }

    private final AuctionLotRepository lots;
    private final AuctionBidRepository bids;
    private final ArtworkRepository artworks;
    private final CoinWalletRepository wallets;
    private final CoinService coins;
    private final TransactionTemplate tx;

    public AuctionEngine(AuctionLotRepository lots, AuctionBidRepository bids, ArtworkRepository artworks,
                         CoinWalletRepository wallets, CoinService coins, PlatformTransactionManager transactionManager) {
        this.lots = lots;
        this.bids = bids;
        this.artworks = artworks;
        this.wallets = wallets;
        this.coins = coins;
        this.tx = new TransactionTemplate(transactionManager);
    }

    static LocalDateTime now() {
        return LocalDateTime.now(Web100tsukuruApplication.ZONE);
    }

    // ------------------------------------------------------------------
    // 出品
    // ------------------------------------------------------------------

    /** 競りを始める（呼ぶ側のトランザクションの中で。絵の行は押さえておく） */
    AuctionLot open(Artwork artwork, Long sellerUserId, String sellerNpc, long startPrice, Length length, LocalDateTime start) {
        long seed = ThreadLocalRandom.current().nextLong();
        String plan = encode(plan(artwork, sellerNpc, startPrice, length, start, seed));
        AuctionLot lot = lots.save(new AuctionLot(artwork.getId(), sellerUserId, sellerNpc, startPrice, start,
                length.minutes, length.extendSeconds, plan, seed));
        artwork.putOnSale(lot.getId());
        return lot;
    }

    /** その日に来ている収集家のうち、開始価格以上を出す人と、来る時刻を決める */
    List<PlanEntry> plan(Artwork artwork, String sellerNpc, long startPrice, Length length, LocalDateTime start, long seed) {
        Day day = AuctionDay.of(start.toLocalDate());
        Features f = Appraisal.features(artwork);
        long artistSales = lots.salesOfArtist(artwork.getArtistId());
        List<PlanEntry> interested = new ArrayList<>();
        Random order = new Random(seed);
        long duration = length.minutes * 60_000L;
        for (ArtCollector c : day.attendees()) {
            if (c.key().equals(sellerNpc)) {
                continue;
            }
            long v = Appraisal.value(c, f, day.trend(), artistSales, artwork.getSaleCount(), artwork.getLastPrice(),
                    new Random(seed ^ c.key().hashCode()));
            if (v >= startPrice) {
                interested.add(new PlanEntry(c.key(), Math.min(v, MAX_PRICE), 0));
            }
        }
        java.util.Collections.shuffle(interested, order);
        List<PlanEntry> result = new ArrayList<>();
        for (int i = 0; i < interested.size(); i++) {
            PlanEntry e = interested.get(i);
            // 1人目はすぐ（3分の競りなら5〜18秒）、ほかの人は後半に寄せて来る
            double at = i == 0 ? 0.03 + 0.07 * order.nextDouble() : 0.12 + 0.83 * Math.pow(order.nextDouble(), 0.8);
            result.add(new PlanEntry(e.key(), e.max(), Math.max(4000, (long) (duration * at))));
        }
        return result;
    }

    static String encode(List<PlanEntry> plan) {
        StringBuilder sb = new StringBuilder();
        for (PlanEntry e : plan) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(e.key()).append(':').append(e.max()).append(':').append(e.arriveMs());
        }
        return sb.toString();
    }

    static List<PlanEntry> decode(String plan) {
        List<PlanEntry> result = new ArrayList<>();
        if (plan == null || plan.isBlank()) {
            return result;
        }
        for (String part : plan.split(";")) {
            String[] p = part.split(":");
            if (p.length == 3) {
                result.add(new PlanEntry(p[0], Long.parseLong(p[1]), Long.parseLong(p[2])));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 進める・片づける
    // ------------------------------------------------------------------

    /** 1つの競りを今まで進め、終わっていれば片づける */
    @Transactional
    public AuctionLot advance(Long lotId) {
        AuctionLot lot = lots.lockById(lotId).orElse(null);
        if (lot != null) {
            advance(lot, now());
        }
        return lot;
    }

    /** 終わりの時刻を過ぎた競りをまとめて片づけ、収集家が持っている絵を出し直す */
    public void settleDue() {
        LocalDateTime now = now();
        for (Long id : lots.idsDue(now, PageRequest.of(0, 30))) {
            tx.executeWithoutResult(s -> lots.lockById(id).ifPresent(lot -> advance(lot, now)));
        }
        tx.executeWithoutResult(s -> relistByCollectors(now));
    }

    void advance(AuctionLot lot, LocalDateTime now) {
        if (!lot.isOpen()) {
            return;
        }
        List<PlanEntry> plan = decode(lot.getPlan());
        Map<String, Integer> counts = new HashMap<>();
        for (AuctionBid b : bids.findByLotIdOrderByIdAsc(lot.getId())) {
            if (b.getNpc() != null) {
                counts.merge(b.getNpc(), 1, Integer::sum);
            }
        }
        for (int guard = 0; guard < 500; guard++) {
            long min = minimumBid(lot);
            PlanEntry next = null;
            LocalDateTime nextAt = null;
            for (PlanEntry e : plan) {
                if (e.key().equals(lot.getTopNpc()) || e.max() < min) {
                    continue;
                }
                int k = counts.getOrDefault(e.key(), 0);
                LocalDateTime arrive = lot.getStartAt().plus(Duration.ofMillis(e.arriveMs()));
                LocalDateTime at = (k == 0 && arrive.isAfter(lot.getLastBidAt()))
                        ? arrive
                        : lot.getLastBidAt().plus(Duration.ofMillis(reactMillis(lot, e.key(), k)));
                if (nextAt == null || at.isBefore(nextAt)) {
                    next = e;
                    nextAt = at;
                }
            }
            if (next == null || nextAt.isAfter(now) || !nextAt.isBefore(lot.getEndAt())) {
                break;
            }
            npcBid(lot, next, nextAt, counts.getOrDefault(next.key(), 0));
            counts.merge(next.key(), 1, Integer::sum);
        }
        if (!now.isBefore(lot.getEndAt())) {
            settle(lot, now);
        }
    }

    /** 0〜1 の値（競り・収集家・回数で決まる） */
    static double unit(long seed, String key, int k, int salt) {
        long h = AuctionDay.mix(seed ^ ((long) key.hashCode() << 20) ^ ((long) k << 8) ^ salt);
        return (h >>> 11) * 0x1.0p-53;
    }

    /** 抜かれてから入れ直すまでの時間（残り時間の4〜24%。ただし2.5〜6.5秒より短くはしない） */
    static long reactMillis(AuctionLot lot, String key, int k) {
        long remaining = Math.max(0, Duration.between(lot.getLastBidAt(), lot.getEndAt()).toMillis());
        long d = (long) (remaining * (0.04 + 0.2 * unit(lot.getSeed(), key, k, 1)));
        long floor = 2500 + (long) (4000 * unit(lot.getSeed(), key, k, 2));
        return Math.max(floor, d);
    }

    private void npcBid(AuctionLot lot, PlanEntry e, LocalDateTime at, int k) {
        long inc = increment(lot.getPrice());
        long min = minimumBid(lot);
        long amount;
        if (k == 0) {
            // はじめての入札は、自分の上限の4〜6割から入る（1人しか来なくても、安く持っていかれないように）
            long target = Math.round(e.max() * (0.4 + 0.2 * unit(lot.getSeed(), e.key(), k, 3)) / inc) * inc;
            amount = Math.max(min, target);
        } else {
            amount = min + inc * (long) (3 * unit(lot.getSeed(), e.key(), k, 4));
        }
        amount = Math.min(amount, e.max());
        ArtCollector c = ArtCollectors.find(e.key()).orElseThrow();
        String line = k == 0
                ? pick(c.openLines(), lot.getSeed(), e.key(), k)
                : (unit(lot.getSeed(), e.key(), k, 5) < 0.35 ? pick(c.raiseLines(), lot.getSeed(), e.key(), k) : null);
        bids.save(new AuctionBid(lot.getId(), null, e.key(), amount, at, false, line));
        if (lot.getTopUserId() != null && amount <= lot.getTopMax()) {
            // 人の上限のほうが高い：人の入札が自動で上がる（同じ額なら先に入れた人の勝ち）
            long raised = Math.min(lot.getTopMax(), amount + increment(amount));
            bids.save(new AuctionBid(lot.getId(), lot.getTopUserId(), null, raised, at, true, null));
            lot.priceUp(raised, at);
        } else {
            lot.bidBy(null, e.key(), amount, 0, at);
        }
    }

    static String pick(List<String> lines, long seed, String key, int k) {
        return lines.get((int) (unit(seed, key, k, 6) * lines.size()));
    }

    private void settle(AuctionLot lot, LocalDateTime now) {
        Artwork artwork = artworks.lockById(lot.getArtworkId()).orElse(null);
        if (!lot.hasTop()) {
            lot.settle(Status.UNSOLD, now);
            if (artwork != null) {
                artwork.unsold();
                if (lot.getSellerNpc() != null) {
                    artwork.npcFailed();
                    artwork.scheduleRelist(artwork.getNpcFails() >= 3 ? null : now.plusDays(1));
                }
            }
            return;
        }
        long price = lot.getPrice();
        Long artworkId = lot.getArtworkId();
        Long artistId = artwork == null ? null : artwork.getArtistId();
        // 財布は番号の小さい順に押さえる（同時に別の競りを片づけても、待ち合って止まらないように）
        List<Long> people = new ArrayList<>();
        for (Long id : new Long[]{lot.getTopUserId(), lot.getSellerUserId(), artistId}) {
            if (id != null && !people.contains(id)) {
                people.add(id);
            }
        }
        people.sort(Long::compare);
        people.forEach(wallets::lockById);

        if (lot.getTopUserId() != null) {
            coins.add(lot.getTopUserId(), -price, Reason.BUY, artworkId);
        }
        boolean resale = artistId != null && !artistId.equals(lot.getSellerUserId());
        long royalty = resale ? Math.round(price * ROYALTY) : 0;
        if (lot.getSellerUserId() != null) {
            long fee = Math.round(price * FEE);
            coins.add(lot.getSellerUserId(), price - fee - royalty, Reason.SELL, artworkId);
        }
        if (royalty > 0) {
            coins.add(artistId, royalty, Reason.ROYALTY, artworkId);
        }
        if (artwork != null) {
            artwork.moveTo(lot.getTopUserId(), lot.getTopNpc());
            artwork.sold(price);
            if (lot.getTopNpc() != null) {
                // 収集家は1〜3日飾ってから、また競りに出す
                long hours = 24 + (long) (48 * unit(lot.getSeed(), lot.getTopNpc(), 99, 7));
                artwork.scheduleRelist(lot.getEndAt().plusHours(hours));
            }
        }
        if (lot.getTopNpc() != null) {
            ArtCollectors.find(lot.getTopNpc()).ifPresent(c -> bids.save(new AuctionBid(lot.getId(), null, c.key(), price,
                    lot.getEndAt(), true, pick(c.winLines(), lot.getSeed(), c.key(), 100))));
        }
        lot.settle(Status.SOLD, now);
    }

    /** 収集家が飾り終えた絵を、また1時間の競りに出す（同時に出しておくのは3つまで） */
    void relistByCollectors(LocalDateTime now) {
        long live = lots.countLiveByNpc(now);
        if (live >= NPC_LIVE_LIMIT) {
            return;
        }
        for (Artwork due : artworks.dueForNpcRelist(now, PageRequest.of(0, (int) (NPC_LIVE_LIMIT - live)))) {
            Artwork a = artworks.lockById(due.getId()).orElse(null);
            if (a == null || a.getOwnerNpc() == null || a.isOnSale()) {
                continue;
            }
            long start = Math.round(a.getLastPrice() * NPC_MARKUP * Math.pow(0.9, a.getNpcFails()) / 10.0) * 10;
            open(a, null, a.getOwnerNpc(), Math.max(MIN_START, start), Length.HOUR_1, now);
        }
    }

    // ------------------------------------------------------------------
    // 人の入札
    // ------------------------------------------------------------------

    /** 上限額を決めて入札する */
    @Transactional
    public BidOutcome bid(Long userId, Long lotId, long amount) {
        AuctionLot lot = lots.lockById(lotId).orElse(null);
        if (lot == null) {
            return BidOutcome.ENDED;
        }
        LocalDateTime now = now();
        advance(lot, now);
        if (!lot.isOpen() || !now.isBefore(lot.getEndAt())) {
            return BidOutcome.ENDED;
        }
        if (lot.isSellerUser(userId)) {
            return BidOutcome.OWN;
        }
        // 同じ人が別の競りに同時に入札しても、使えるコインを二重に数えないように財布の行を押さえる
        if (wallets.lockById(userId).isEmpty()) {
            return BidOutcome.NOT_ENOUGH;
        }
        CoinService.Wallet wallet = coins.wallet(userId);
        if (lot.isTopUser(userId)) {
            if (amount <= lot.getTopMax()) {
                return BidOutcome.TOO_LOW;
            }
            if (amount - lot.getTopMax() > wallet.available()) {
                return BidOutcome.NOT_ENOUGH;
            }
            lot.raiseMax(Math.min(amount, MAX_PRICE));
            return BidOutcome.RAISED;
        }
        long min = minimumBid(lot);
        if (amount < min || amount > MAX_PRICE) {
            return BidOutcome.TOO_LOW;
        }
        if (amount > wallet.available()) {
            return BidOutcome.NOT_ENOUGH;
        }
        if (!lot.hasTop()) {
            bids.save(new AuctionBid(lot.getId(), userId, null, lot.getStartPrice(), now, false, null));
            lot.bidBy(userId, null, lot.getStartPrice(), amount, now);
            return BidOutcome.TOP;
        }
        if (lot.getTopNpc() != null) {
            bids.save(new AuctionBid(lot.getId(), userId, null, min, now, false, null));
            lot.bidBy(userId, null, min, amount, now);
            return BidOutcome.TOP;
        }
        // いちばん高いのが別の人：上限どうしで比べる
        long otherMax = lot.getTopMax();
        Long other = lot.getTopUserId();
        if (amount <= otherMax) {
            bids.save(new AuctionBid(lot.getId(), userId, null, amount, now, false, null));
            long raised = Math.min(otherMax, amount + increment(amount));
            bids.save(new AuctionBid(lot.getId(), other, null, raised, now, true, null));
            lot.priceUp(raised, now);
            return BidOutcome.OUTBID;
        }
        long price = Math.min(amount, otherMax + increment(otherMax));
        if (otherMax > lot.getPrice()) {
            bids.save(new AuctionBid(lot.getId(), other, null, otherMax, now, true, null));
        }
        bids.save(new AuctionBid(lot.getId(), userId, null, price, now, false, null));
        lot.bidBy(userId, null, price, amount, now);
        return BidOutcome.TOP;
    }
}
