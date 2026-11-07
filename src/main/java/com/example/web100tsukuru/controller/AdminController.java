package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.service.FictionService;
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
    private final byte[] passwordHash;
    // 合言葉の総当たり対策（同じ接続元から 10分に5回まで）
    private final PostRateLimiter loginLimiter = new PostRateLimiter(5, Duration.ofMinutes(10));

    public AdminController(FictionService fictionService, @Value("${app.admin-password-hash:}") String passwordHash) {
        this.fictionService = fictionService;
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

    /** チェックを入れた投稿を消す (POST /admin/delete) */
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
