package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.VoteLogin;
import com.example.web100tsukuru.service.auction.AuctionEngine;
import com.example.web100tsukuru.service.auction.AuctionViews;
import com.example.web100tsukuru.service.auction.CoinService;
import com.example.web100tsukuru.service.auction.CoinService.Greeting;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 「らくがきオークション」の画面すべてで、最初にすること。
 * 1. 終わりの時刻を過ぎた競りを片づけ、収集家が飾り終えた絵を出し直す
 * 2. ログインしていれば、その人（me）とコインの残り（wallet）を画面に渡す。
 *    はじめてなら最初のコインを、今日まだならログインボーナスを渡す
 */
@ControllerAdvice(assignableTypes = {AuctionController.class, AccountController.class})
public class AuctionModelAdvice {

    private final AuctionEngine engine;
    private final CoinService coins;
    private final AccountService accounts;
    private final VoteLogin login;

    public AuctionModelAdvice(AuctionEngine engine, CoinService coins, AccountService accounts, VoteLogin login) {
        this.engine = engine;
        this.coins = coins;
        this.accounts = accounts;
        this.login = login;
    }

    @ModelAttribute
    public void currentUser(HttpServletRequest request, Model model) {
        if (!request.getRequestURI().startsWith("/auction")) {
            return;
        }
        engine.settleDue();
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return;
        }
        accounts.find(userId).ifPresent(user -> {
            Greeting greeting = coins.greet(userId);
            model.addAttribute("me", AuctionViews.user(user.getId(), user.getDisplayName()));
            model.addAttribute("meId", user.getId());
            model.addAttribute("wallet", coins.wallet(userId));
            if (greeting.started()) {
                model.addAttribute("greeting", "はじめての参加なので、コインを" + String.format("%,d", coins.getStartCoins()) + "枚受け取りました。");
            } else if (greeting.bonus() > 0) {
                model.addAttribute("greeting", "ログインボーナスとして、コインを" + greeting.bonus() + "枚受け取りました。");
            }
        });
    }
}
