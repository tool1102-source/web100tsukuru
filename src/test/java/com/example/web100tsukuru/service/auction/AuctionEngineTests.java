package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.Artwork;
import com.example.web100tsukuru.entity.AuctionBid;
import com.example.web100tsukuru.entity.AuctionLot;
import com.example.web100tsukuru.entity.AuctionLot.Status;
import com.example.web100tsukuru.entity.CoinWallet;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.ArtworkImageRepository;
import com.example.web100tsukuru.repository.ArtworkReportRepository;
import com.example.web100tsukuru.repository.ArtworkRepository;
import com.example.web100tsukuru.repository.AuctionBidRepository;
import com.example.web100tsukuru.repository.AuctionLotRepository;
import com.example.web100tsukuru.repository.CoinLedgerRepository;
import com.example.web100tsukuru.repository.CoinWalletRepository;
import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.auction.AuctionEngine.BidOutcome;
import com.example.web100tsukuru.service.auction.AuctionEngine.Length;
import com.example.web100tsukuru.service.auction.ArtworkService.ArtworkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 競りの進め方・入札・お金の動き（収集家の動きは予定を直接書いて確かめる） */
@SpringBootTest
class AuctionEngineTests {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired AuctionEngine engine;
    @Autowired ArtworkService artworks;
    @Autowired CoinService coins;
    @Autowired AccountService accounts;
    @Autowired ArtworkRepository artworkRepo;
    @Autowired ArtworkImageRepository images;
    @Autowired ArtworkReportRepository reports;
    @Autowired AuctionLotRepository lots;
    @Autowired AuctionBidRepository bids;
    @Autowired CoinWalletRepository wallets;
    @Autowired CoinLedgerRepository ledger;
    @Autowired AppUserRepository users;
    @Autowired PlatformTransactionManager transactionManager;

    TransactionTemplate tx;

    @BeforeEach
    void clean() {
        tx = new TransactionTemplate(transactionManager);
        bids.deleteAll();
        lots.deleteAll();
        reports.deleteAll();
        images.deleteAll();
        artworkRepo.deleteAll();
        ledger.deleteAll();
        wallets.deleteAll();
    }

    private Long user(String name) throws Exception {
        AppUser u = accounts.signup("auc_" + SEQ.incrementAndGet(), name, "password123");
        coins.greet(u.getId());
        return u.getId();
    }

    private long coinsOf(Long userId) {
        return wallets.findById(userId).map(CoinWallet::getCoins).orElse(0L);
    }

    /** 左半分を赤、右半分を青で塗った 512×512 の絵 */
    static byte[] png(Consumer<Graphics2D> paint) throws IOException {
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 512, 512);
        paint.accept(g);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static byte[] redAndBlue() throws IOException {
        return png(g -> {
            g.setColor(new Color(0xe23b3b));
            g.fillRect(0, 0, 256, 256);
            g.setColor(new Color(0x2f5fd0));
            g.fillRect(256, 0, 256, 256);
        });
    }

    /** 収集家の予定を自分で決めた競り（始まりは start、長さ3分） */
    private AuctionLot lotWith(Long sellerId, Artwork artwork, long startPrice, LocalDateTime start, String plan) {
        return tx.execute(s -> {
            Artwork a = artworkRepo.lockById(artwork.getId()).orElseThrow();
            AuctionLot lot = lots.save(new AuctionLot(a.getId(), sellerId, null, startPrice, start, 3, 30, plan, 42L));
            a.putOnSale(lot.getId());
            return lot;
        });
    }

    private AuctionLot advanceTo(Long lotId, LocalDateTime at) {
        return tx.execute(s -> {
            AuctionLot lot = lots.lockById(lotId).orElseThrow();
            engine.advance(lot, at);
            return lot;
        });
    }

    @Test
    void 届いた絵は13色に直して特徴を測る() throws Exception {
        Drawing.Result r = Drawing.read(redAndBlue());
        assertThat(r.features().ink()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(r.features().colors()).isEqualTo(2);
        assertThat(r.features().share()[3]).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(r.features().share()[8]).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        // 16色の枠の PNG にし直すので、届いたものより小さい
        assertThat(r.png().length).isLessThan(redAndBlue().length);
        BufferedImage back = ImageIO.read(new java.io.ByteArrayInputStream(r.png()));
        assertThat(back.getWidth()).isEqualTo(512);
        assertThat(back.getRGB(10, 10) & 0xffffff).isEqualTo(0xe23b3b);
        // パレットにない色は、いちばん近い色に直す
        Drawing.Result near = Drawing.read(png(g -> { g.setColor(new Color(0xe5393a)); g.fillRect(0, 0, 100, 100); }));
        assertThat(near.features().share()[3]).isEqualTo(1.0);
        // 大きさが違う・画像でないものは受け付けない
        BufferedImage small = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(small, "png", out);
        assertThatThrownBy(() -> Drawing.read(out.toByteArray())).isInstanceOf(Drawing.InvalidDrawingException.class);
        assertThatThrownBy(() -> Drawing.read("abc".getBytes())).isInstanceOf(Drawing.InvalidDrawingException.class);
    }

    @Test
    void 白紙と題名なしは保存せず_1日5枚まで() throws Exception {
        Long a = user("画家");
        assertThatThrownBy(() -> artworks.create(a, "白紙", png(g -> { })))
                .isInstanceOf(ArtworkException.class).hasMessageContaining("白紙");
        assertThatThrownBy(() -> artworks.create(a, " ", redAndBlue()))
                .isInstanceOf(ArtworkException.class).hasMessageContaining("題名");
        for (int i = 0; i < 5; i++) {
            artworks.create(a, "絵" + i, redAndBlue());
        }
        assertThat(artworks.remainingToday(a)).isZero();
        assertThatThrownBy(() -> artworks.create(a, "6枚目", redAndBlue()))
                .isInstanceOf(ArtworkException.class).hasMessageContaining("5枚まで");
    }

    @Test
    void はじめてならコイン1000枚_次の日はログインボーナス() throws Exception {
        Long a = user("新人");
        assertThat(coinsOf(a)).isEqualTo(1000);
        assertThat(coins.greet(a)).isEqualTo(CoinService.Greeting.NONE);
        tx.executeWithoutResult(s -> wallets.lockById(a).orElseThrow().bonusGiven(LocalDate.now().minusDays(1)));
        assertThat(coins.greet(a).bonus()).isEqualTo(100);
        assertThat(coinsOf(a)).isEqualTo(1100);
    }

    @Test
    void 人どうしは上限額で競り_払うのは勝った値段だけ() throws Exception {
        Long seller = user("出す人");
        Long b = user("買う人B");
        Long c = user("買う人C");
        Artwork art = artworks.create(seller, "人どうし", redAndBlue());
        AuctionLot lot = lotWith(seller, art, 10, LocalDateTime.now().minusSeconds(30), "");

        assertThat(engine.bid(b, lot.getId(), 200)).isEqualTo(BidOutcome.TOP);
        assertThat(lots.findById(lot.getId()).orElseThrow().getPrice()).isEqualTo(10);
        assertThat(coins.wallet(b).available()).isEqualTo(800);

        // C の上限は B より低い：B が自動で 160 まで上がる
        assertThat(engine.bid(c, lot.getId(), 150)).isEqualTo(BidOutcome.OUTBID);
        AuctionLot now = lots.findById(lot.getId()).orElseThrow();
        assertThat(now.getPrice()).isEqualTo(160);
        assertThat(now.getTopUserId()).isEqualTo(b);

        // C が上限を上げて抜く：値段は B の上限＋刻み
        assertThat(engine.bid(c, lot.getId(), 300)).isEqualTo(BidOutcome.TOP);
        now = lots.findById(lot.getId()).orElseThrow();
        assertThat(now.getPrice()).isEqualTo(210);
        assertThat(now.getTopUserId()).isEqualTo(c);
        assertThat(coins.wallet(b).hold()).isZero();
        assertThat(coins.wallet(c).hold()).isEqualTo(300);

        // 足りないコイン・低すぎる額・自分の絵には入札できない
        assertThat(engine.bid(b, lot.getId(), 5000)).isEqualTo(BidOutcome.NOT_ENOUGH);
        assertThat(engine.bid(b, lot.getId(), 210)).isEqualTo(BidOutcome.TOO_LOW);
        assertThat(engine.bid(seller, lot.getId(), 1000)).isEqualTo(BidOutcome.OWN);

        // 終わったら C が 210 で落札：出品者には手数料5%を引いた 200（四捨五入で 199.5 → 200）
        AuctionLot ended = advanceTo(lot.getId(), now.getEndAt().plusSeconds(1));
        assertThat(ended.getStatus()).isEqualTo(Status.SOLD);
        assertThat(coinsOf(c)).isEqualTo(1000 - 210);
        assertThat(coinsOf(seller)).isEqualTo(1000 + 210 - Math.round(210 * 0.05));
        Artwork moved = artworkRepo.findById(art.getId()).orElseThrow();
        assertThat(moved.getOwnerUserId()).isEqualTo(c);
        assertThat(moved.getLastPrice()).isEqualTo(210);
        assertThat(moved.isOnSale()).isFalse();
        assertThat(coins.wallet(c).hold()).isZero();
        assertThat(engine.bid(b, lot.getId(), 500)).isEqualTo(BidOutcome.ENDED);
    }

    @Test
    void 転売されると描いた人に5パーセント入る() throws Exception {
        Long artist = user("描いた人");
        Long b = user("一人目");
        Long c = user("二人目");
        Artwork art = artworks.create(artist, "転売", redAndBlue());
        AuctionLot first = lotWith(artist, art, 100, LocalDateTime.now().minusSeconds(10), "");
        engine.bid(b, first.getId(), 100);
        advanceTo(first.getId(), first.getEndAt().plusSeconds(1));
        long artistAfterFirst = coinsOf(artist);
        assertThat(artistAfterFirst).isEqualTo(1000 + 95);

        AuctionLot second = lotWith(b, artworkRepo.findById(art.getId()).orElseThrow(), 400, LocalDateTime.now().minusSeconds(10), "");
        engine.bid(c, second.getId(), 400);
        advanceTo(second.getId(), second.getEndAt().plusSeconds(1));
        // B は 400 − 手数料20 − 作者へ20 = 360、作者は 20
        assertThat(coinsOf(b)).isEqualTo(1000 - 100 + 360);
        assertThat(coinsOf(artist)).isEqualTo(artistAfterFirst + 20);
        assertThat(coinsOf(c)).isEqualTo(600);
    }

    @Test
    void 収集家は来た時刻に入札し_抜かれたら少しして入れ直す() throws Exception {
        Long seller = user("出す人");
        Long b = user("挑む人");
        Artwork art = artworks.create(seller, "収集家と", redAndBlue());
        LocalDateTime start = LocalDateTime.now().minusSeconds(60);
        // 赤坂伯爵が始まって1秒で来て、500 まで出す
        AuctionLot lot = lotWith(seller, art, 10, start, "akasaka:500:1000");

        // 来る前は、まだだれも入札していない
        AuctionLot before = advanceTo(lot.getId(), start.plusNanos(500_000_000));
        assertThat(before.hasTop()).isFalse();

        // 人が入札する前に、伯爵の最初の入札（上限の4〜6割）が入る
        assertThat(engine.bid(b, lot.getId(), 400)).isEqualTo(BidOutcome.TOP);
        List<AuctionBid> log = bids.findByLotIdOrderByIdAsc(lot.getId());
        assertThat(log.get(0).getNpc()).isEqualTo("akasaka");
        assertThat(log.get(0).getAmount()).isBetween(200L, 300L);
        assertThat(log.get(0).getLine()).isNotBlank();
        AuctionLot mid = lots.findById(lot.getId()).orElseThrow();
        assertThat(mid.getTopUserId()).isEqualTo(b);

        // しばらくすると伯爵が入れ直す。人の上限 400 までは自動で上がり、最後は伯爵が抜いて落とす
        AuctionLot ended = advanceTo(lot.getId(), LocalDateTime.now().plusMinutes(10));
        assertThat(ended.getStatus()).isEqualTo(Status.SOLD);
        assertThat(ended.getTopNpc()).isEqualTo("akasaka");
        assertThat(ended.getPrice()).isGreaterThan(400).isLessThanOrEqualTo(500);
        assertThat(coins.wallet(b).hold()).isZero();
        assertThat(coinsOf(b)).isEqualTo(1000);
        // 人の上限まで、自動で競り上がった記録がある
        assertThat(bids.findByLotIdOrderByIdAsc(lot.getId())).anyMatch(x -> b.equals(x.getUserId()) && x.isAuto());

        // 終わると伯爵のもの。出品した人にはコインが入り、伯爵は1〜3日後にまた出す
        Artwork owned = artworkRepo.findById(art.getId()).orElseThrow();
        assertThat(owned.getOwnerNpc()).isEqualTo("akasaka");
        assertThat(owned.getOwnerUserId()).isNull();
        assertThat(owned.getRelistAt()).isAfter(ended.getEndAt().plusHours(23)).isBefore(ended.getEndAt().plusHours(73));
        assertThat(coinsOf(seller)).isEqualTo(1000 + ended.getPrice() - Math.round(ended.getPrice() * 0.05));
        assertThat(bids.findByLotIdOrderByIdAsc(lot.getId()).get(bids.findByLotIdOrderByIdAsc(lot.getId()).size() - 1).isAuto()).isTrue();

        // 出し直す時刻を過ぎると、前の値段の1.2倍から1時間の競りに出る
        tx.executeWithoutResult(s -> engine.relistByCollectors(owned.getRelistAt().plusMinutes(1)));
        Artwork relisted = artworkRepo.findById(art.getId()).orElseThrow();
        assertThat(relisted.isOnSale()).isTrue();
        AuctionLot npcLot = lots.findById(relisted.getOpenLotId()).orElseThrow();
        assertThat(npcLot.getSellerNpc()).isEqualTo("akasaka");
        assertThat(npcLot.getDurationMinutes()).isEqualTo(60);
        assertThat(npcLot.getStartPrice()).isEqualTo(Math.round(ended.getPrice() * 1.2 / 10.0) * 10);
        // 出品した本人（伯爵）は、自分の競りには来ない
        assertThat(npcLot.getPlan()).doesNotContain("akasaka");
    }

    @Test
    void 終わる直前の入札で終わりが延びる() throws Exception {
        Long seller = user("出す人");
        Long b = user("滑りこむ人");
        Artwork art = artworks.create(seller, "延長", redAndBlue());
        // 3分の競りで、残り10秒
        LocalDateTime start = LocalDateTime.now().minusSeconds(170);
        AuctionLot lot = lotWith(seller, art, 10, start, "");
        engine.bid(b, lot.getId(), 50);
        AuctionLot after = lots.findById(lot.getId()).orElseThrow();
        assertThat(after.getEndAt()).isAfter(start.plusMinutes(3).plusSeconds(15));
        assertThat(after.getEndAt()).isBefore(LocalDateTime.now().plusSeconds(31));
    }

    @Test
    void だれも入札しなければ売れずに手元へ戻る() throws Exception {
        Long seller = user("出す人");
        Artwork art = artworks.create(seller, "売れない", redAndBlue());
        AuctionLot lot = lotWith(seller, art, 10, LocalDateTime.now().minusMinutes(5), "");
        AuctionLot ended = advanceTo(lot.getId(), LocalDateTime.now());
        assertThat(ended.getStatus()).isEqualTo(Status.UNSOLD);
        Artwork back = artworkRepo.findById(art.getId()).orElseThrow();
        assertThat(back.getOwnerUserId()).isEqualTo(seller);
        assertThat(back.isOnSale()).isFalse();
        assertThat(coinsOf(seller)).isEqualTo(1000);
        // また出せる
        assertThat(artworks.sell(seller, art.getId(), 10, Length.MINUTES_3).isOpen()).isTrue();
        assertThatThrownBy(() -> artworks.sell(seller, art.getId(), 10, Length.MINUTES_3))
                .isInstanceOf(ArtworkException.class).hasMessageContaining("出品中");
    }

    @Test
    void ほかの人の絵は出品できず_出品すると来場者から予定が決まる() throws Exception {
        Long seller = user("出す人");
        Long other = user("ほかの人");
        Artwork art = artworks.create(seller, "予定", redAndBlue());
        assertThatThrownBy(() -> artworks.sell(other, art.getId(), 10, Length.MINUTES_3))
                .isInstanceOf(ArtworkException.class);
        AuctionLot lot = artworks.sell(seller, art.getId(), 10, Length.HOUR_1);
        AuctionDay.Day day = AuctionDay.of(lot.getStartAt().toLocalDate());
        for (AuctionEngine.PlanEntry e : AuctionEngine.decode(lot.getPlan())) {
            assertThat(day.attends(e.key())).isTrue();
            assertThat(e.max()).isGreaterThanOrEqualTo(10);
            assertThat(e.arriveMs()).isBetween(4000L, 60L * 60_000);
        }
        // 赤と青が半分ずつの絵は、来ていれば赤坂伯爵か青柳夫人が気に入る
        boolean redOrBlueFan = day.attends("akasaka") || day.attends("aoyagi");
        if (redOrBlueFan) {
            assertThat(lot.getPlan()).containsAnyOf("akasaka", "aoyagi");
        }
    }

    @Test
    void 消した絵は競りごと取り消し_買った人に値段を返す() throws Exception {
        Long artist = user("描いた人");
        Long b = user("買った人");
        Long c = user("入札中の人");
        Artwork art = artworks.create(artist, "消される", redAndBlue());
        AuctionLot first = lotWith(artist, art, 100, LocalDateTime.now().minusSeconds(10), "");
        engine.bid(b, first.getId(), 100);
        advanceTo(first.getId(), first.getEndAt().plusSeconds(1));
        AuctionLot second = lotWith(b, artworkRepo.findById(art.getId()).orElseThrow(), 100, LocalDateTime.now().minusSeconds(10), "");
        engine.bid(c, second.getId(), 300);
        assertThat(coins.wallet(c).hold()).isEqualTo(300);

        assertThat(artworks.deleteAll(List.of(art.getId()))).hasSize(1);
        assertThat(artworkRepo.findById(art.getId())).isEmpty();
        assertThat(images.findById(art.getId())).isEmpty();
        assertThat(lots.findByArtworkId(art.getId())).isEmpty();
        assertThat(coins.wallet(c).hold()).isZero();
        assertThat(coinsOf(b)).isEqualTo(1000);
    }

    @Test
    void 報告は1人1回まで数える() throws Exception {
        Long artist = user("描いた人");
        Long r = user("報告する人");
        Artwork art = artworks.create(artist, "報告", redAndBlue());
        assertThat(artworks.report(r, art.getId())).isTrue();
        assertThat(artworks.report(r, art.getId())).isFalse();
        assertThat(artworkRepo.findById(art.getId()).orElseThrow().getReportCount()).isEqualTo(1);
    }

    @Test
    void 収集家の付け値は好みと流行で変わる() {
        double[] red = new double[13];
        red[3] = 1.0;
        Drawing.Features redArt = new Drawing.Features(0.4, 0.05, 1, red);
        ArtCollectors.ArtCollector akasaka = ArtCollectors.find("akasaka").orElseThrow();
        ArtCollectors.ArtCollector aoyagi = ArtCollectors.find("aoyagi").orElseThrow();
        AuctionDay.Trend redTrend = AuctionDay.TRENDS.get(0);
        AuctionDay.Trend blueTrend = AuctionDay.TRENDS.get(3);
        // 青い絵が好きな人は、真っ赤な絵には値を付けない
        assertThat(Appraisal.value(aoyagi, redArt, redTrend, 0, 0, 0, new java.util.Random(1))).isZero();
        long withTrend = Appraisal.value(akasaka, redArt, redTrend, 0, 0, 0, new java.util.Random(1));
        long without = Appraisal.value(akasaka, redArt, blueTrend, 0, 0, 0, new java.util.Random(1));
        assertThat((double) withTrend / without).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.01));
        // 作者の絵がたくさん売れているほど高い
        assertThat(Appraisal.value(akasaka, redArt, blueTrend, 100, 0, 0, new java.util.Random(1))).isGreaterThan(without);
        // 日付が同じなら、来場者と流行は同じ。毎日5人
        assertThat(AuctionDay.of(LocalDate.of(2026, 11, 10))).isEqualTo(AuctionDay.of(LocalDate.of(2026, 11, 10)));
        assertThat(AuctionDay.of(LocalDate.of(2026, 11, 11)).attendees()).hasSize(5);
    }
}
