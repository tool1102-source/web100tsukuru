package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.AuctionLot;
import com.example.web100tsukuru.service.VoteLogin;
import com.example.web100tsukuru.service.auction.AuctionEngine;
import com.example.web100tsukuru.service.auction.AuctionViews;
import com.example.web100tsukuru.service.auction.AuctionViews.BidLine;
import com.example.web100tsukuru.service.auction.AuctionViews.Who;
import com.example.web100tsukuru.service.auction.CoinService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 競りの画面が2秒ごとに読みにくる「いまの状態」(GET /auction/lots/{id}/state)。
 * ほかの画面のように終わった競りの片づけまではせず、この競りだけを今まで進める。
 */
@RestController
public class AuctionLiveController {

    private final AuctionEngine engine;
    private final AuctionViews views;
    private final CoinService coins;
    private final VoteLogin login;

    public AuctionLiveController(AuctionEngine engine, AuctionViews views, CoinService coins, VoteLogin login) {
        this.engine = engine;
        this.views = views;
        this.coins = coins;
        this.login = login;
    }

    static long millis(LocalDateTime t) {
        return t.atZone(Web100tsukuruApplication.ZONE).toInstant().toEpochMilli();
    }

    static Map<String, Object> who(Who w) {
        if (w == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", w.name());
        m.put("initial", w.initial());
        m.put("color", w.color());
        m.put("href", w.href());
        m.put("npc", w.npc());
        return m;
    }

    @GetMapping("/auction/lots/{id:\\d+}/state")
    public ResponseEntity<Map<String, Object>> state(@PathVariable Long id, HttpServletRequest request) {
        AuctionLot lot = engine.advance(id);
        if (lot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<AuctionViews.LotCard> cards = views.lotCards(List.of(lot));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", lot.getStatus().name());
        body.put("price", lot.getPrice());
        body.put("startPrice", lot.getStartPrice());
        body.put("minBid", AuctionEngine.minimumBid(lot));
        body.put("bidCount", lot.getBidCount());
        body.put("endAt", millis(lot.getEndAt()));
        body.put("now", System.currentTimeMillis());
        body.put("top", cards.isEmpty() ? null : who(cards.get(0).top()));
        List<BidLine> lines = views.bidLines(id);
        Long userId = login.currentUserId(request);
        if (userId != null) {
            String mine = "/auction/users/" + userId;
            boolean bidden = lines.stream().anyMatch(b -> !b.who().npc() && mine.equals(b.who().href()));
            Map<String, Object> me = new LinkedHashMap<>();
            me.put("top", lot.isTopUser(userId));
            me.put("max", lot.isTopUser(userId) ? lot.getTopMax() : 0);
            me.put("seller", lot.isSellerUser(userId));
            // 入札したことがあって、いまはいちばん高くない（抜かれた）
            me.put("outbid", lot.isOpen() && bidden && !lot.isTopUser(userId));
            me.put("won", lot.getStatus() == AuctionLot.Status.SOLD && lot.isTopUser(userId));
            CoinService.Wallet wallet = coins.wallet(userId);
            me.put("coins", wallet.coins());
            me.put("available", wallet.available());
            body.put("me", me);
        }
        body.put("bids", lines.stream().map(AuctionLiveController::bid).toList());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static Map<String, Object> bid(BidLine b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id());
        m.put("who", who(b.who()));
        m.put("amount", b.amount());
        m.put("at", millis(b.at()));
        m.put("auto", b.auto());
        m.put("line", b.line());
        m.put("win", b.win());
        return m;
    }
}
