package com.example.web100tsukuru.service;

import com.example.web100tsukuru.entity.AppSecret;
import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.repository.AppSecretRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 「ちいさな投票所」のログイン状態。
 *
 * セッションだけだと、サーバーが作り直されるたび（公開のたび・休止のあと）にログインが切れるので、
 * 署名した Cookie（vote_login）も渡して90日はログインしたままにする。
 * 中身は「利用者の番号.期限.署名」。署名の鍵はサーバーだけが持つ（AppSecret）。
 * パスワードの BCrypt の値も署名に混ぜるので、パスワードが変われば古い Cookie は使えなくなる。
 * Cookie は SameSite=Lax なので、よそのサイトから投票などを送らせても付いていかない。
 */
@Component
public class VoteLogin {

    private static final String SESSION_KEY = "voteUserId";
    private static final String COOKIE = "vote_login";
    private static final Duration LIFETIME = Duration.ofDays(90);
    private static final String SECRET_NAME = "vote-login";

    private final AppSecretRepository secrets;
    private final AccountService accounts;
    private final boolean secureCookie;
    private volatile byte[] key;

    public VoteLogin(AppSecretRepository secrets, AccountService accounts,
                     @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        this.secrets = secrets;
        this.accounts = accounts;
        this.secureCookie = secureCookie;
    }

    /** ログインしている人の番号（していなければ null）。Cookie しかなければ、セッションにも入れ直す */
    public Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(SESSION_KEY);
        if (id instanceof Long userId) {
            return userId;
        }
        Optional<Long> fromCookie = readCookie(request);
        fromCookie.ifPresent(userId -> request.getSession(true).setAttribute(SESSION_KEY, userId));
        return fromCookie.orElse(null);
    }

    public void login(HttpServletRequest request, HttpServletResponse response, AppUser user) {
        // ログイン前のセッションは使い回さない（セッション固定攻撃の対策）
        HttpSession old = request.getSession(false);
        if (old != null) {
            request.changeSessionId();
        }
        request.getSession(true).setAttribute(SESSION_KEY, user.getId());
        long expires = Instant.now().plus(LIFETIME).getEpochSecond();
        String payload = user.getId() + "." + expires;
        writeCookie(response, payload + "." + sign(payload, user.getPasswordHash()), LIFETIME);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
        writeCookie(response, "", Duration.ZERO);
    }

    private Optional<Long> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (!COOKIE.equals(cookie.getName())) {
                continue;
            }
            String[] parts = cookie.getValue().split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            try {
                long userId = Long.parseLong(parts[0]);
                long expires = Long.parseLong(parts[1]);
                if (Instant.now().getEpochSecond() > expires) {
                    return Optional.empty();
                }
                Optional<AppUser> user = accounts.find(userId);
                if (user.isEmpty()) {
                    return Optional.empty();
                }
                String expected = sign(parts[0] + "." + parts[1], user.get().getPasswordHash());
                boolean ok = MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                        parts[2].getBytes(StandardCharsets.US_ASCII));
                return ok ? Optional.of(userId) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String sign(String payload, String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            mac.update(payload.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 署名の鍵。DB になければ作る（同時に作られたら、先に入った方を使う） */
    private byte[] key() {
        byte[] k = key;
        if (k != null) {
            return k;
        }
        synchronized (this) {
            if (key == null) {
                key = secrets.findById(SECRET_NAME).map(AppSecret::getSecret).orElseGet(() -> {
                    byte[] fresh = new byte[32];
                    new SecureRandom().nextBytes(fresh);
                    try {
                        return secrets.saveAndFlush(new AppSecret(SECRET_NAME, fresh)).getSecret();
                    } catch (DataIntegrityViolationException e) {
                        return secrets.findById(SECRET_NAME).orElseThrow().getSecret();
                    }
                });
            }
            return key;
        }
    }
}
