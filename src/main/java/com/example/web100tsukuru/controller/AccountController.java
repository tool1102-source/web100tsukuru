package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.AccountService.SignupException;
import com.example.web100tsukuru.service.GoogleIdTokens;
import com.example.web100tsukuru.service.GoogleIdTokens.GoogleIdentity;
import com.example.web100tsukuru.service.PollService;
import com.example.web100tsukuru.service.PostRateLimiter;
import com.example.web100tsukuru.service.VoteLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

/** 「ちいさな投票所」の登録・ログイン（ID とパスワード、Google）・マイページ・利用規約 */
@Controller
public class AccountController {

    // Google で来て、まだ表示名を決めていない人の Google アカウントの番号と、戻る先（セッションに置く）
    private static final String PENDING_GOOGLE_SUB = "pendingGoogleSub";
    private static final String PENDING_NEXT = "pendingNext";

    private final AccountService accounts;
    private final PollService polls;
    private final VoteLogin login;
    private final GoogleIdTokens google;
    // 投票券ほしさにアカウントを作りまくられないように、同じ接続元からの登録は1日3件まで
    private final PostRateLimiter signupLimiter;
    // パスワードの総当たり対策（同じ接続元から 10分に10回まで）
    private final PostRateLimiter loginLimiter;

    public AccountController(AccountService accounts, PollService polls, VoteLogin login, GoogleIdTokens google,
                             @Value("${app.vote.signup-limit:3}") int signupLimit,
                             @Value("${app.vote.login-limit:10}") int loginLimit) {
        this.accounts = accounts;
        this.polls = polls;
        this.login = login;
        this.google = google;
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
            redirect.addFlashAttribute("notice", "登録が完了しました。投票券を" + accounts.getSignupTickets() + "枚受け取りました。");
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
            model.addAttribute("error", "短い時間に続けてログインが試されたため、受け付けを一時的に止めています。10分ほどたってから、もう一度お試しください。");
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
            redirect.addFlashAttribute("notice", "ログインボーナスとして、投票券を" + bonus + "枚受け取りました。");
        }
        return "redirect:" + safeNext(next);
    }

    /**
     * 「Google でログイン」のボタンから届く (POST /vote/login/google)。
     * 登録ずみならそのままログイン。はじめての人は、表示名を決める画面へ。
     */
    @PostMapping("/vote/login/google")
    public String googleLogin(@RequestParam(defaultValue = "") String credential,
                              @RequestParam(required = false) String next,
                              HttpServletRequest request, HttpServletResponse response,
                              Model model, RedirectAttributes redirect) {
        Optional<GoogleIdentity> identity = google.verify(credential);
        if (identity.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            model.addAttribute("next", safeNext(next));
            model.addAttribute("error", "Google でのログインを確認できませんでした。もう一度お試しください。");
            return "vote/login";
        }
        Optional<AppUser> user = accounts.findByGoogleSub(identity.get().subject());
        if (user.isPresent()) {
            login.login(request, response, user.get());
            long bonus = accounts.grantDailyBonus(user.get().getId());
            if (bonus > 0) {
                redirect.addFlashAttribute("notice", "ログインボーナスとして、投票券を" + bonus + "枚受け取りました。");
            }
            return "redirect:" + safeNext(next);
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(PENDING_GOOGLE_SUB, identity.get().subject());
        session.setAttribute(PENDING_NEXT, safeNext(next));
        return "redirect:/vote/signup/google";
    }

    /** Google ではじめて来た人に、表示名を決めてもらう (GET /vote/signup/google) */
    @GetMapping("/vote/signup/google")
    public String googleSignupForm(HttpServletRequest request, Model model) {
        if (pendingGoogleSub(request) == null) {
            return "redirect:/vote/login";
        }
        model.addAttribute("signupTickets", accounts.getSignupTickets());
        return "vote/google-name";
    }

    @PostMapping("/vote/signup/google")
    public String googleSignup(@RequestParam(defaultValue = "") String displayName,
                               @RequestParam(defaultValue = "") String website,
                               HttpServletRequest request, HttpServletResponse response,
                               Model model, RedirectAttributes redirect) {
        String sub = pendingGoogleSub(request);
        if (sub == null) {
            return "redirect:/vote/login";
        }
        model.addAttribute("signupTickets", accounts.getSignupTickets());
        model.addAttribute("displayName", displayName);
        if (!website.isBlank()) {
            return "redirect:/vote";
        }
        if (!signupLimiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("error", "この接続元からの登録が多すぎます。明日もう一度お試しください。");
            return "vote/google-name";
        }
        try {
            AppUser user = accounts.signupWithGoogle(sub, displayName);
            HttpSession session = request.getSession(false);
            String next = session == null ? "/vote" : (String) session.getAttribute(PENDING_NEXT);
            if (session != null) {
                session.removeAttribute(PENDING_GOOGLE_SUB);
                session.removeAttribute(PENDING_NEXT);
            }
            login.login(request, response, user);
            redirect.addFlashAttribute("notice", "登録が完了しました。投票券を" + accounts.getSignupTickets() + "枚受け取りました。");
            return "redirect:" + safeNext(next);
        } catch (SignupException e) {
            model.addAttribute("error", e.getMessage());
            return "vote/google-name";
        }
    }

    private static String pendingGoogleSub(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(PENDING_GOOGLE_SUB);
    }

    /** 「ちいさな投票所」の利用規約 (GET /vote/terms) */
    @GetMapping("/vote/terms")
    public String terms(Model model) {
        model.addAttribute("signupTickets", accounts.getSignupTickets());
        model.addAttribute("dailyBonus", accounts.getDailyBonus());
        return "vote/terms";
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
