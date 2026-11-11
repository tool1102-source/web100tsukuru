package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.AccountService.SignupException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 「らくがきオークション公式」のアカウント。パスワードはだれも知らない（ログインできない）。
 * 公式の絵の持ち主になり、管理画面から競りに出す。ランキングには出さず、転売の5%も受け取らない。
 */
@Component
public class OfficialAccount {

    public static final String LOGIN_ID = "rakugaki_official";
    public static final String DISPLAY_NAME = "らくがきオークション公式";

    private final AppUserRepository users;
    private final AccountService accounts;
    private volatile Long id;

    public OfficialAccount(AppUserRepository users, AccountService accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    /** 公式のアカウントの番号（なければ作る） */
    public synchronized Long ensure() {
        if (id != null && users.existsById(id)) {
            return id;
        }
        AppUser user = users.findByLoginId(LOGIN_ID).orElseGet(() -> {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            try {
                return accounts.signup(LOGIN_ID, DISPLAY_NAME, Base64.getEncoder().encodeToString(secret));
            } catch (SignupException e) {
                throw new IllegalStateException(e);
            }
        });
        id = user.getId();
        return id;
    }

    /** 公式のアカウントの番号（まだなければ null） */
    public Long find() {
        Long current = id;
        if (current == null) {
            current = users.findByLoginId(LOGIN_ID).map(AppUser::getId).orElse(null);
            id = current;
        }
        return current;
    }

    public boolean is(Long userId) {
        return userId != null && userId.equals(find());
    }
}
