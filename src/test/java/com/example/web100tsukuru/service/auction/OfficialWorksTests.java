package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.Artwork;
import com.example.web100tsukuru.entity.AuctionLot;
import com.example.web100tsukuru.entity.CoinWallet;
import com.example.web100tsukuru.repository.ArtworkImageRepository;
import com.example.web100tsukuru.repository.ArtworkReportRepository;
import com.example.web100tsukuru.repository.ArtworkRepository;
import com.example.web100tsukuru.repository.AuctionBidRepository;
import com.example.web100tsukuru.repository.AuctionLotRepository;
import com.example.web100tsukuru.repository.CoinLedgerRepository;
import com.example.web100tsukuru.repository.CoinWalletRepository;
import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.auction.AuctionEngine.Length;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 公式の絵（名画のらくがき版）：登録・出品・転売の5%なし・ランキングに出ない */
@SpringBootTest
class OfficialWorksTests {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired OfficialWorks officialWorks;
    @Autowired OfficialAccount official;
    @Autowired ArtworkService artworks;
    @Autowired AuctionEngine engine;
    @Autowired AuctionViews views;
    @Autowired CoinService coins;
    @Autowired AccountService accounts;
    @Autowired ArtworkRepository artworkRepo;
    @Autowired ArtworkImageRepository images;
    @Autowired ArtworkReportRepository reports;
    @Autowired AuctionLotRepository lots;
    @Autowired AuctionBidRepository bids;
    @Autowired CoinWalletRepository wallets;
    @Autowired CoinLedgerRepository ledger;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        bids.deleteAll();
        lots.deleteAll();
        reports.deleteAll();
        images.deleteAll();
        artworkRepo.deleteAll();
        ledger.deleteAll();
        wallets.deleteAll();
    }

    @Test
    void 名画13枚を公式の持ち物として登録し_二度は登録しない() {
        assertThat(officialWorks.load()).isEqualTo(13);
        assertThat(officialWorks.load()).isZero();
        Long id = official.find();
        List<Artwork> stock = artworkRepo.findByOwnerUserIdOrderByIdDesc(id);
        assertThat(stock).hasSize(13);
        assertThat(stock).allMatch(a -> a.getCredit() != null && a.getArtistId().equals(id) && a.getTitle().endsWith("（らくがき版）"));
        assertThat(stock).allMatch(a -> a.getInk() > 0.05);
        // 好みの違う収集家が、それぞれ気に入る絵がある（どの収集家にも好きな公式の絵がある）
        for (ArtCollectors.ArtCollector c : ArtCollectors.ALL) {
            assertThat(stock).as(c.name()).anyMatch(a -> c.tasteFor(Appraisal.features(a)) >= Appraisal.LIKES);
        }
        assertThat(views.officialStock()).hasSize(13);
    }

    @Test
    void 公式の絵を売っても描いた人への5パーセントはなく_公式はランキングに出ない() throws Exception {
        officialWorks.load();
        Long officialId = official.ensure();
        AppUser buyer = accounts.signup("off_" + SEQ.incrementAndGet(), "買う人", "password123");
        coins.greet(buyer.getId());
        Artwork art = artworkRepo.findByOwnerUserIdOrderByIdDesc(officialId).get(0);
        AuctionLot lot = artworks.sell(officialId, art.getId(), 100, Length.HOUR_1);
        assertThat(engine.bid(buyer.getId(), lot.getId(), 300)).isIn(AuctionEngine.BidOutcome.TOP, AuctionEngine.BidOutcome.OUTBID);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        AuctionLot ended = tx.execute(s -> {
            AuctionLot l = lots.lockById(lot.getId()).orElseThrow();
            engine.advance(l, LocalDateTime.now().plusHours(3));
            return l;
        });
        assertThat(ended.getStatus()).isEqualTo(AuctionLot.Status.SOLD);
        long price = ended.getPrice();
        if (ended.getTopUserId() != null) {
            // 人が落とした：公式には95%、作者への5%はない
            assertThat(wallets.findById(officialId).map(CoinWallet::getCoins).orElse(0L))
                    .isEqualTo(price - Math.round(price * 0.05));
        }
        assertThat(views.assets(50)).noneMatch(a -> a.who().name().equals(OfficialAccount.DISPLAY_NAME));
        assertThat(views.artists(50)).noneMatch(a -> a.who().name().equals(OfficialAccount.DISPLAY_NAME));
    }
}
