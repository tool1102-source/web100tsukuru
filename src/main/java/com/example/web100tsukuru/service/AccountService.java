package com.example.web100tsukuru.service;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.TicketLedger;
import com.example.web100tsukuru.entity.TicketLedger.Reason;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.TicketLedgerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** 「ちいさな投票所」の登録・ログイン・ログインボーナス */
@Service
@Transactional(readOnly = true)
public class AccountService {

    public static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9_]{3,20}$");
    public static final int PASSWORD_MIN = 8;
    public static final int DISPLAY_NAME_MAX = 20;

    private final AppUserRepository users;
    private final TicketLedgerRepository ledger;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final long signupTickets;
    private final long dailyBonus;
    // ログイン ID がないときも、あるときと同じくらい時間をかける（ID があるかどうかを時間で悟らせない）
    private final String dummyHash = encoder.encode("dummy-password-for-timing");

    public AccountService(AppUserRepository users, TicketLedgerRepository ledger,
                          @Value("${app.vote.signup-tickets:100}") long signupTickets,
                          @Value("${app.vote.daily-bonus:10}") long dailyBonus) {
        this.users = users;
        this.ledger = ledger;
        this.signupTickets = signupTickets;
        this.dailyBonus = dailyBonus;
    }

    public long getSignupTickets() {
        return signupTickets;
    }

    public long getDailyBonus() {
        return dailyBonus;
    }

    /** 登録できないときの理由（画面にそのまま出す） */
    public static class SignupException extends Exception {
        public SignupException(String message) {
            super(message);
        }
    }

    /**
     * 登録して、はじめての投票券を渡す。登録した日はログインボーナスを渡したことにする。
     */
    @Transactional
    public AppUser signup(String loginId, String displayName, String password) throws SignupException {
        String id = loginId == null ? "" : loginId.strip();
        String name = displayName == null ? "" : displayName.strip();
        if (!LOGIN_ID.matcher(id).matches()) {
            throw new SignupException("ログイン ID は、英小文字・数字・_ の3〜20文字にしてください。");
        }
        if (name.isEmpty() || name.codePointCount(0, name.length()) > DISPLAY_NAME_MAX) {
            throw new SignupException("表示名は1〜" + DISPLAY_NAME_MAX + "文字にしてください。");
        }
        if (password == null || password.length() < PASSWORD_MIN || password.length() > 72) {
            throw new SignupException("パスワードは" + PASSWORD_MIN + "文字以上にしてください。");
        }
        if (users.existsByLoginId(id)) {
            throw new SignupException("そのログイン ID は、もう使われています。");
        }
        AppUser user = new AppUser(id, name, encoder.encode(password));
        user.addTickets(signupTickets, 0);
        user.setLastBonusDate(today());
        users.save(user);
        ledger.save(new TicketLedger(user.getId(), signupTickets, 0, Reason.SIGNUP, null));
        return user;
    }

    /** ID とパスワードが合っていれば、その人 */
    public Optional<AppUser> authenticate(String loginId, String password) {
        Optional<AppUser> user = users.findByLoginId(loginId == null ? "" : loginId.strip());
        String hash = user.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean ok = password != null && encoder.matches(password, hash);
        return ok ? user : Optional.empty();
    }

    public Optional<AppUser> find(Long userId) {
        return userId == null ? Optional.empty() : users.findById(userId);
    }

    /**
     * 今日まだならログインボーナスを渡す。渡した枚数を返す（もう渡していれば 0）。
     * 画面を開くたびに呼ばれるので、まず読むだけで確かめ、渡すときだけ行を押さえて書く。
     */
    @Transactional
    public long grantDailyBonus(Long userId) {
        LocalDate today = today();
        Optional<AppUser> peek = users.findById(userId);
        if (peek.isEmpty() || today.equals(peek.get().getLastBonusDate())) {
            return 0;
        }
        AppUser user = users.lockById(userId).orElseThrow();
        if (today.equals(user.getLastBonusDate())) {
            return 0;
        }
        user.addTickets(dailyBonus, 0);
        user.setLastBonusDate(today);
        ledger.save(new TicketLedger(user.getId(), dailyBonus, 0, Reason.DAILY_BONUS, null));
        return dailyBonus;
    }

    /** マイページに出す、最近の券の出入り */
    public List<TicketLedger> recentLedger(Long userId) {
        return ledger.findTop50ByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    private static LocalDate today() {
        return LocalDate.now(Web100tsukuruApplication.ZONE);
    }
}
