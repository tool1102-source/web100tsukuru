package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.AccountService.SignupException;
import com.example.web100tsukuru.service.PollService;
import com.example.web100tsukuru.service.PostRateLimiter;
import com.example.web100tsukuru.service.VoteLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.Optional;

/** 「ちいさな投票所」の登録・ログイン・マイページ */
@Controller
public class AccountController {

    private final AccountService accounts;
    private final PollService polls;
    private final VoteLogin login;
    // 投票券ほしさにアカウントを作りまくられないように、同じ接続元からの登録は1日3件まで
    private final PostRateLimiter signupLimiter;
    // パスワードの総当たり対策（同じ接続元から 10分に10回まで）
    private final PostRateLimiter loginLimiter;

    public AccountController(AccountService accounts, PollService polls, VoteLogin login,
                             @Value("${app.vote.signup-limit:3}") int signupLimit,
                             @Value("${app.vote.login-limit:10}") int loginLimit) {
        this.accounts = accounts;
        this.polls = polls;
        this.login = login;
        this.signupLimiter = new PostRateLimiter(signupLimit, Duration.ofDays(1));
        this.loginLimiter = new PostRateLimiter(loginLimit, Duration.ofMinutes(10));
    }

    @GetMapping("/vote/signup")
    public String signupForm(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", safeNext(next));
        model.addAttribute("signupTickets", accounts.getSignupTickets());
        return "vote/signup";
    }

    @PostMapping("/vote/signup")
    public String signup(@RequestParam(defaultValue = "") String loginId,
                         @RequestParam(defaultValue = "") String displayName,
                         @RequestParam(defaultValue = "") String password,
                         @RequestParam(defaultValue = "") String website,
                         @RequestParam(required = false) String next,
                         HttpServletRequest request, HttpServletResponse response,
                         Model model, RedirectAttributes redirect) {
        model.addAttribute("next", safeNext(next));
        model.addAttribute("signupTickets", accounts.getSignupTickets());
        model.addAttribute("loginId", loginId);
        model.addAttribute("displayName", displayName);
        // おとり欄に入力がある＝ボット。登録せず、一覧へ
        if (!website.isBlank()) {
            return "redirect:/vote";
        }
        if (!signupLimiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("error", "この接続元からの登録が多すぎます。明日もう一度お試しください。");
            return "vote/signup";
        }
        try {
            AppUser user = accounts.signup(loginId, displayName, password);
            login.login(request, response, user);
            redirect.addFlashAttribute("notice", "登録しました。投票券を" + accounts.getSignupTickets() + "枚どうぞ。");
            return "redirect:" + safeNext(next);
        } catch (SignupException e) {
            model.addAttribute("error", e.getMessage());
            return "vote/signup";
        }
    }

    @GetMapping("/vote/login")
    public String loginForm(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", safeNext(next));
        return "vote/login";
    }

    @PostMapping("/vote/login")
    public String doLogin(@RequestParam(defaultValue = "") String loginId,
                          @RequestParam(defaultValue = "") String password,
                          @RequestParam(required = false) String next,
                          HttpServletRequest request, HttpServletResponse response,
                          Model model, RedirectAttributes redirect) {
        model.addAttribute("next", safeNext(next));
        model.addAttribute("loginId", loginId);
        if (!loginLimiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("error", "短い時間に何度も試したため、受け付けを止めています。10分ほどたってから、もう一度お試しください。");
            return "vote/login";
        }
        Optional<AppUser> user = accounts.authenticate(loginId, password);
        if (user.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            model.addAttribute("error", "ログイン ID かパスワードが違います。");
            return "vote/login";
        }
        login.login(request, response, user.get());
        // ログインした時点でボーナスを渡しておく（次の画面で「もらいました」と出る）
        long bonus = accounts.grantDailyBonus(user.get().getId());
        if (bonus > 0) {
            redirect.addFlashAttribute("notice", "ログインボーナスで投票券を" + bonus + "枚もらいました。");
        }
        return "redirect:" + safeNext(next);
    }

    @PostMapping("/vote/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        login.logout(request, response);
        return "redirect:/vote";
    }

    /** マイページ：券の残り・出入り・自分のお題・券を入れたお題 */
    @GetMapping("/vote/me")
    public String me(HttpServletRequest request, Model model) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return "redirect:/vote/login?next=/vote/me";
        }
        model.addAttribute("ledger", accounts.recentLedger(userId));
        model.addAttribute("owned", polls.ownedBy(userId));
        model.addAttribute("voted", polls.votedBy(userId));
        model.addAttribute("dailyBonus", accounts.getDailyBonus());
        return "vote/me";
    }

    /** ログインのあとに戻る先。よそのサイトへは飛ばさない */
    static String safeNext(String next) {
        if (next != null && next.matches("^/vote(/[\\w\\-/]*)?(\\?[\\w=&\\-]*)?$")) {
            return next;
        }
        return "/vote";
    }
}
