package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.entity.Poll;
import com.example.web100tsukuru.service.FictionService;
import com.example.web100tsukuru.service.PollService;
import com.example.web100tsukuru.service.PostRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.example.web100tsukuru.entity.Artwork;
import com.example.web100tsukuru.service.auction.ArtworkService;
import com.example.web100tsukuru.service.auction.AuctionViews;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * フィクションブログの管理画面（不適切な投稿を消す）。
 *
 * 合言葉そのものはどこにも置かず、SHA-256 にした値だけを app.admin-password-hash に置く。
 * ソースは公開しているので、合言葉は推測できないランダムな24文字にしている（短いと総当たりで割り出される）。
 * 値が空なら管理画面ごと無効（404）。
 * ログインの状態はセッションに持つ。セッションの Cookie は SameSite=Strict なので、
 * よそのサイトから削除のリクエストを送らせることはできない（application.properties）。
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final String SESSION_KEY = "admin";

    private final FictionService fictionService;
    private final PollService pollService;
    private final ArtworkService artworkService;
    private final AuctionViews auctionViews;
    private final byte[] passwordHash;
    // 合言葉の総当たり対策（同じ接続元から 10分に5回まで）
    private final PostRateLimiter loginLimiter = new PostRateLimiter(5, Duration.ofMinutes(10));

    public AdminController(FictionService fictionService, PollService pollService,
                           ArtworkService artworkService, AuctionViews auctionViews,
                           @Value("${app.admin-password-hash:}") String passwordHash) {
        this.fictionService = fictionService;
        this.pollService = pollService;
        this.artworkService = artworkService;
        this.auctionViews = auctionViews;
        this.passwordHash = HexFormat.of().parseHex(passwordHash.strip());
    }

    /** 投稿の一覧。ログインしていなければ合言葉の入力画面 (GET /admin?page=2) */
    @GetMapping
    public String index(@RequestParam(defaultValue = "1") int page, HttpServletRequest request, Model model) {
        requireEnabled();
        if (!isLoggedIn(request)) {
            return "admin/login";
        }
        Page<Fiction> fictions = fictionService.findPage(page);
        if (page < 1 || (page > 1 && page > fictions.getTotalPages())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("fictions", fictions);
        model.addAttribute("page", page);
        // 不適切な画像も見つけられるように、投稿ごとの画像を小さく並べる
        model.addAttribute("imageNumbers", fictionService.imageNumbers(fictions.map(Fiction::getId).toList()));
        return "admin/index";
    }

    /** 合言葉の確認 (POST /admin/login) */
    @PostMapping("/login")
    public String login(@RequestParam(defaultValue = "") String password,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model) {
        requireEnabled();
        if (!loginLimiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("rateLimited", true);
            return "admin/login";
        }
        if (!MessageDigest.isEqual(sha256(password), passwordHash)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            model.addAttribute("wrongPassword", true);
            return "admin/login";
        }
        // ログイン前のセッションは使い回さない（セッション固定攻撃の対策）
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true).setAttribute(SESSION_KEY, true);
        return "redirect:/admin";
    }

    /** ログアウト (POST /admin/logout) */
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/admin";
    }

    /** チェックを入れた投稿を、その画像ごと消す (POST /admin/delete) */
    @PostMapping("/delete")
    public String delete(@RequestParam(name = "ids", required = false) List<Long> ids,
                         HttpServletRequest request,
                         RedirectAttributes redirect) {
        requireEnabled();
        if (!isLoggedIn(request)) {
            return "redirect:/admin";
        }
        List<Fiction> deleted = fictionService.deleteAll(ids == null ? List.of() : ids);
        // 消したものは元に戻せないので、何を消したかだけは Render のログに残す
        for (Fiction f : deleted) {
            log.info("管理画面で投稿を消しました: id={} タイトル={}", f.getId(), f.getTitle().replaceAll("[\\r\\n]+", " "));
        }
        redirect.addFlashAttribute("deletedCount", deleted.size());
        return "redirect:/admin";
    }

    /** 「ちいさな投票所」のお題の一覧 (GET /admin/vote?page=2) */
    @GetMapping("/vote")
    public String polls(@RequestParam(defaultValue = "1") int page, HttpServletRequest request, Model model) {
        requireEnabled();
        if (!isLoggedIn(request)) {
            return "redirect:/admin";
        }
        Page<Poll> found = pollService.page(page, 30);
        if (page < 1 || (page > 1 && page > found.getTotalPages())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("polls", found);
        model.addAttribute("summaries", pollService.summaries(found.getContent()));
        model.addAttribute("page", page);
        return "admin/vote";
    }

    /** チェックを入れたお題を消す。入っていた投票券は、全部入れた人に返す (POST /admin/vote/delete) */
    @PostMapping("/vote/delete")
    public String deletePolls(@RequestParam(name = "ids", required = false) List<Long> ids,
                              HttpServletRequest request,
                              RedirectAttributes redirect) {
        requireEnabled();
        if (!isLoggedIn(request)) {
            return "redirect:/admin";
        }
        List<Poll> deleted = pollService.deleteAll(ids == null ? List.of() : ids);
        for (Poll p : deleted) {
            log.info("管理画面でお題を消しました: id={} タイトル={}", p.getId(), p.getTitle().replaceAll("[\\r\\n]+", " "));
        }
        redirect.addFlashAttribute("deletedCount", deleted.size());
        return "redirect:/admin/vote";
    }

    /** 「らくがきオークション」の絵の一覧（報告のあった絵を先に） (GET /admin/auction) */
    @GetMapping("/auction")
    public String artworks(HttpServletRequest request, Model model) {
        requireEnabled();
        if (!isLoggedIn(request)) {
            return "redirect:/admin";
        }
        model.addAttribute("reported", auctionViews.reported());
        model.addAttribute("newest", auctionViews.newest(60));
        return "admin/auction";
    }

    /** チェックを入れた絵を消す。出品中の競りは取り消し、買って持っていた人には買った値段を返す (POST /admin/auction/delete) */
    @PostMapping("/auction/delete")
    public String deleteArtworks(@RequestParam(name = "ids", required = false) List<Long> ids,
                                 HttpServletRequest request,
                                 RedirectAttributes redirect) {
        requireEnabled();
        if (!isLoggedIn(request)) {
            return "redirect:/admin";
        }
        List<Artwork> deleted = artworkService.deleteAll(ids == null ? List.of() : ids);
        for (Artwork a : deleted) {
            log.info("管理画面で絵を消しました: id={} 題名={}", a.getId(), a.getTitle().replaceAll("[\r\n]+", " "));
        }
        redirect.addFlashAttribute("deletedCount", deleted.size());
        return "redirect:/admin/auction";
    }

    private void requireEnabled() {
        if (passwordHash.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY));
    }
}
