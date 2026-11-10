package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.CoinLedger;
import com.example.web100tsukuru.entity.CoinLedger.Reason;
import com.example.web100tsukuru.entity.CoinWallet;
import com.example.web100tsukuru.repository.AuctionLotRepository;
import com.example.web100tsukuru.repository.CoinLedgerRepository;
import com.example.web100tsukuru.repository.CoinWalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

/**
 * コインの残りと出入り。はじめて来た人には最初のコインを、その後は1日1回ログインボーナスを渡す。
 * 入札中の上限額の分（hold）は、競りが終わるまで別の入札には使えない。
 */
@Service
public class CoinService {

    /** 画面に出す残り。available = 入札に使える分 */
    public record Wallet(long coins, long hold) {
        public long available() {
            return Math.max(0, coins - hold);
        }
    }

    /** 画面を開いたときに渡したもの（はじめての参加・ログインボーナス） */
    public record Greeting(boolean started, long bonus) {
        public static final Greeting NONE = new Greeting(false, 0);
    }

    private final CoinWalletRepository wallets;
    private final CoinLedgerRepository ledger;
    private final AuctionLotRepository lots;
    private final TransactionTemplate tx;
    private final long startCoins;
    private final long dailyBonus;

    public CoinService(CoinWalletRepository wallets, CoinLedgerRepository ledger, AuctionLotRepository lots,
                       PlatformTransactionManager transactionManager,
                       @Value("${app.auction.start-coins:1000}") long startCoins,
                       @Value("${app.auction.daily-bonus:100}") long dailyBonus) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.lots = lots;
        this.tx = new TransactionTemplate(transactionManager);
        this.startCoins = startCoins;
        this.dailyBonus = dailyBonus;
    }

    public long getStartCoins() {
        return startCoins;
    }

    public long getDailyBonus() {
        return dailyBonus;
    }

    /** はじめてなら財布を作って最初のコインを、今日まだならログインボーナスを渡す */
    public Greeting greet(Long userId) {
        LocalDate today = LocalDate.now(Web100tsukuruApplication.ZONE);
        java.util.Optional<CoinWallet> current = wallets.findById(userId);
        if (current.isPresent() && today.equals(current.get().getLastBonusDate())) {
            // ふだんはここで終わる（行を押さえない）
            return Greeting.NONE;
        }
        if (current.isEmpty()) {
            try {
                tx.executeWithoutResult(s -> {
                    wallets.saveAndFlush(new CoinWallet(userId, startCoins, today));
                    ledger.save(new CoinLedger(userId, startCoins, Reason.START, null));
                });
                return new Greeting(true, 0);
            } catch (DataIntegrityViolationException e) {
                // 同時に開いた別の画面が先に作った
                return Greeting.NONE;
            }
        }
        Long bonus = tx.execute(s -> {
            CoinWallet w = wallets.lockById(userId).orElse(null);
            if (w == null || today.equals(w.getLastBonusDate()) || dailyBonus <= 0) {
                return 0L;
            }
            w.bonusGiven(today);
            w.add(dailyBonus);
            ledger.save(new CoinLedger(userId, dailyBonus, Reason.DAILY_BONUS, null));
            return dailyBonus;
        });
        return bonus == null || bonus == 0 ? Greeting.NONE : new Greeting(false, bonus);
    }

    @Transactional(readOnly = true)
    public Wallet wallet(Long userId) {
        long coins = wallets.findById(userId).map(CoinWallet::getCoins).orElse(0L);
        return new Wallet(coins, lots.holdOf(userId));
    }

    /** コインを増減する（呼ぶ側のトランザクションの中で。財布の行を押さえてから） */
    void add(Long userId, long delta, Reason reason, Long artworkId) {
        if (delta == 0) {
            return;
        }
        CoinWallet w = wallets.lockById(userId).orElseGet(() ->
                wallets.save(new CoinWallet(userId, 0, LocalDate.now(Web100tsukuruApplication.ZONE))));
        w.add(delta);
        ledger.save(new CoinLedger(userId, delta, reason, artworkId));
    }

    @Transactional(readOnly = true)
    public List<CoinLedger> recent(Long userId) {
        return ledger.findTop30ByUserIdOrderByIdDesc(userId);
    }
}
