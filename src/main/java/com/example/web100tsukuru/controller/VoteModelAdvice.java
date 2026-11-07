package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.PollSettlement;
import com.example.web100tsukuru.service.VoteLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 「ちいさな投票所」の画面すべてで、最初にすること。
 * 1. 締め切りを過ぎたお題の、1位以外の券を返す（残りの枚数を正しく出すため）
 * 2. ログインしていれば、その人（me）を画面に渡し、今日まだならログインボーナスを渡す
 */
@ControllerAdvice(assignableTypes = {PollController.class, AccountController.class})
public class VoteModelAdvice {

    private final PollSettlement settlement;
    private final AccountService accounts;
    private final VoteLogin login;

    public VoteModelAdvice(PollSettlement settlement, AccountService accounts, VoteLogin login) {
        this.settlement = settlement;
        this.accounts = accounts;
        this.login = login;
    }

    @ModelAttribute
    public void currentUser(HttpServletRequest request, Model model) {
        settlement.settleAllDue();
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return;
        }
        long bonus = accounts.grantDailyBonus(userId);
        accounts.find(userId).ifPresent(me -> {
            model.addAttribute("me", me);
            if (bonus > 0) {
                model.addAttribute("bonusGranted", bonus);
            }
        });
    }
}
