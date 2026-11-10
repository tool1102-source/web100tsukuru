package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.Artwork;
import com.example.web100tsukuru.entity.AuctionLot;
import com.example.web100tsukuru.service.PostRateLimiter;
import com.example.web100tsukuru.service.VoteLogin;
import com.example.web100tsukuru.service.auction.ArtCollectors;
import com.example.web100tsukuru.service.auction.ArtCollectors.ArtCollector;
import com.example.web100tsukuru.service.auction.ArtworkService;
import com.example.web100tsukuru.service.auction.ArtworkService.ArtworkException;
import com.example.web100tsukuru.service.auction.AuctionDay;
import com.example.web100tsukuru.service.auction.AuctionDay.Day;
import com.example.web100tsukuru.service.auction.AuctionEngine;
import com.example.web100tsukuru.service.auction.AuctionEngine.BidOutcome;
import com.example.web100tsukuru.service.auction.AuctionEngine.Length;
import com.example.web100tsukuru.service.auction.AuctionViews;
import com.example.web100tsukuru.service.auction.CoinService;
import com.example.web100tsukuru.service.auction.FramedImage;
import com.example.web100tsukuru.service.auction.Palette;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** No.004「らくがきオークション」の画面 */
@Controller
public class AuctionController {

    // 何を描くか迷ったときの「お題」
    static final List<String> PROMPTS = List.of(
            "ねこ", "いぬ", "富士山", "好きな食べ物", "自画像", "宇宙人", "朝ごはん", "夏休み", "おばけ", "ロボット",
            "海", "雪だるま", "ラーメン", "恐竜", "花火", "王様", "魚", "家", "お城", "電車",
            "夕焼け", "雨の日", "きのこ", "怪獣", "UFO", "ケーキ", "森", "鳥", "ドラゴン", "自分の部屋",
            "未来の乗りもの", "変な生きもの", "月", "火山", "宝箱", "忍者", "花", "かえる", "島", "パンダ");

    private final AuctionEngine engine;
    private final ArtworkService artworks;
    private final AuctionViews views;
    private final CoinService coins;
    private final VoteLogin login;
    // 入札の連打を止める（同じ人から 1分に30回まで）
    private final PostRateLimiter bidLimiter;

    public AuctionController(AuctionEngine engine, ArtworkService artworks, AuctionViews views, CoinService coins,
                             VoteLogin login, @Value("${app.auction.bid-limit:30}") int bidLimit) {
        this.engine = engine;
        this.artworks = artworks;
        this.views = views;
        this.coins = coins;
        this.login = login;
        this.bidLimiter = new PostRateLimiter(bidLimit, Duration.ofMinutes(1));
    }

    static LocalDate today() {
        return LocalDate.now(Web100tsukuruApplication.ZONE);
    }

    private static void addDays(Model model) {
        Day today = AuctionDay.of(today());
        model.addAttribute("today", today);
        model.addAttribute("tomorrow", AuctionDay.of(today().plusDays(1)));
    }

    private static String toLogin(String next) {
        return "redirect:/auction/login?next=" + next;
    }

    /** トップ：開催中の競り・今日の来場者と流行の色・最近の落札・名画 */
    @GetMapping("/auction")
    public String home(Model model) {
        addDays(model);
        model.addAttribute("live", views.live(12));
        model.addAttribute("sold", views.recentSales(8));
        model.addAttribute("masterpieces", views.masterpieces(6));
        model.addAttribute("newest", views.newest(8));
        return "auction/home";
    }

    /** 描く画面 */
    @GetMapping("/auction/draw")
    public String draw(HttpServletRequest request, Model model) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return toLogin("/auction/draw");
        }
        addDays(model);
        model.addAttribute("palette", Palette.COLORS);
        model.addAttribute("prompts", PROMPTS);
        model.addAttribute("remaining", artworks.remainingToday(userId));
        model.addAttribute("dailyLimit", artworks.getDailyLimit());
        model.addAttribute("titleMax", ArtworkService.TITLE_MAX);
        return "auction/draw";
    }

    /** 描いた絵を保存する（描く画面から fetch で届く。答えは JSON） */
    @PostMapping("/auction/artworks")
    public ResponseEntity<Map<String, Object>> create(@RequestParam(defaultValue = "") String title,
                                                      @RequestParam(name = "image", required = false) MultipartFile image,
                                                      HttpServletRequest request) throws IOException {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "ログインが切れました。もう一度ログインしてください。"));
        }
        try {
            Artwork a = artworks.create(userId, title, image == null ? null : image.getBytes());
            return ResponseEntity.ok(Map.of("url", "/auction/artworks/" + a.getId()));
        } catch (ArtworkException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 絵のページ：来歴・好みそうな収集家・出品 */
    @GetMapping("/auction/artworks/{id:\\d+}")
    public String artwork(@PathVariable Long id, HttpServletRequest request, Model model) {
        Artwork a = artworks.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long userId = login.currentUserId(request);
        addDays(model);
        Day today = (Day) model.getAttribute("today");
        Day tomorrow = (Day) model.getAttribute("tomorrow");
        model.addAttribute("card", views.cards(List.of(a)).get(0));
        model.addAttribute("history", views.provenance(id));
        model.addAttribute("fans", AuctionViews.fans(a, today, tomorrow));
        model.addAttribute("trendToday", AuctionViews.trendMatch(a, today));
        model.addAttribute("trendTomorrow", AuctionViews.trendMatch(a, tomorrow));
        model.addAttribute("mine", a.isOwnedBy(userId));
        model.addAttribute("lengths", Length.values());
        model.addAttribute("minStart", AuctionEngine.MIN_START);
        model.addAttribute("ogImage", "/auction/artworks/" + id + "/og.jpg");
        return "auction/artwork";
    }

    @GetMapping("/auction/artworks/{id:\\d+}.png")
    public ResponseEntity<byte[]> png(@PathVariable Long id) {
        byte[] png = artworks.png(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // 消した絵が中継サーバーから配られ続けないように、覚えてよいのは見た人のブラウザだけ
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(png);
    }

    /** 共有したときの画像（額縁に入れた絵） */
    @GetMapping("/auction/artworks/{id:\\d+}/og.jpg")
    public ResponseEntity<byte[]> og(@PathVariable Long id) throws IOException {
        byte[] png = artworks.png(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(FramedImage.render(png));
    }

    /** 持っている絵を競りに出す */
    @PostMapping("/auction/artworks/{id:\\d+}/sell")
    public String sell(@PathVariable Long id, @RequestParam(defaultValue = "0") long startPrice,
                       @RequestParam(defaultValue = "MINUTES_3") String length,
                       HttpServletRequest request, RedirectAttributes redirect) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return toLogin("/auction/artworks/" + id);
        }
        try {
            AuctionLot lot = artworks.sell(userId, id, startPrice, Length.of(length));
            redirect.addFlashAttribute("notice", "競りを始めました。収集家が来るのを待ちましょう。");
            return "redirect:/auction/lots/" + lot.getId();
        } catch (ArtworkException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/auction/artworks/" + id;
        }
    }

    @PostMapping("/auction/artworks/{id:\\d+}/report")
    public String report(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return toLogin("/auction/artworks/" + id);
        }
        artworks.report(userId, id);
        redirect.addFlashAttribute("notice", "報告しました。管理人が確認します。");
        return "redirect:/auction/artworks/" + id;
    }

    /** 競りの画面（開いている間は auction-lot.js が2秒ごとに /state を読みにくる） */
    @GetMapping("/auction/lots/{id:\\d+}")
    public String lot(@PathVariable Long id, Model model) {
        AuctionLot lot = engine.advance(id);
        if (lot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<AuctionViews.LotCard> cards = views.lotCards(List.of(lot));
        if (cards.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("card", cards.get(0));
        model.addAttribute("bids", views.bidLines(id));
        model.addAttribute("minBid", AuctionEngine.minimumBid(lot));
        model.addAttribute("lotDay", AuctionDay.of(lot.getStartAt().toLocalDate()));
        // 画面の時計は、サーバーの時刻との差を見て合わせる
        model.addAttribute("endMs", AuctionLiveController.millis(lot.getEndAt()));
        model.addAttribute("plannedMs", AuctionLiveController.millis(lot.getStartAt().plusMinutes(lot.getDurationMinutes())));
        model.addAttribute("nowMs", System.currentTimeMillis());
        model.addAttribute("ogImage", "/auction/artworks/" + lot.getArtworkId() + "/og.jpg");
        return "auction/lot";
    }

    /** 入札する（画面の JavaScript から。答えは JSON） */
    @PostMapping(value = "/auction/lots/{id:\\d+}/bid", headers = "X-Requested-With=fetch")
    public ResponseEntity<Map<String, Object>> bidJson(@PathVariable Long id, @RequestParam(defaultValue = "0") long amount,
                                                       HttpServletRequest request) {
        Long userId = login.currentUserId(request);
        Map<String, Object> body = new LinkedHashMap<>();
        if (userId == null) {
            body.put("ok", false);
            body.put("message", "入札するにはログインしてください。");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
        BidOutcome outcome = placeBid(userId, id, amount);
        body.put("ok", outcome != null && outcome.ok());
        body.put("outcome", outcome == null ? "LIMITED" : outcome.name());
        body.put("message", message(outcome));
        return ResponseEntity.ok(body);
    }

    /** 入札する（JavaScript が動かないときのフォームから。画面を出し直す） */
    @PostMapping("/auction/lots/{id:\\d+}/bid")
    public String bid(@PathVariable Long id, @RequestParam(defaultValue = "0") long amount,
                      HttpServletRequest request, RedirectAttributes redirect) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return toLogin("/auction/lots/" + id);
        }
        BidOutcome outcome = placeBid(userId, id, amount);
        redirect.addFlashAttribute(outcome != null && outcome.ok() ? "notice" : "error", message(outcome));
        return "redirect:/auction/lots/" + id;
    }

    private BidOutcome placeBid(Long userId, Long lotId, long amount) {
        if (!bidLimiter.tryAcquire("u" + userId)) {
            return null;
        }
        return engine.bid(userId, lotId, amount);
    }

    private static String message(BidOutcome outcome) {
        return outcome == null ? "入札が続いたため、少し待ってからお試しください。" : outcome.getMessage();
    }

    /** マイページ：コイン・入札中・出品中・持っている絵・描いた絵・コインの記録 */
    @GetMapping("/auction/me")
    public String me(HttpServletRequest request, Model model) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return toLogin("/auction/me");
        }
        model.addAttribute("leading", views.leading(userId));
        model.addAttribute("selling", views.selling(userId));
        model.addAttribute("owned", views.ownedBy(userId));
        model.addAttribute("drawn", views.drawnBy(userId));
        model.addAttribute("ledger", coins.recent(userId));
        model.addAttribute("remaining", artworks.remainingToday(userId));
        return "auction/me";
    }

    /** ほかの人のページ：持っている絵・描いた絵 */
    @GetMapping("/auction/users/{id:\\d+}")
    public String user(@PathVariable Long id, Model model) {
        AuctionViews.Who who = views.who(id);
        List<AuctionViews.WorkCard> drawn = views.drawnBy(id);
        List<AuctionViews.WorkCard> owned = views.ownedBy(id);
        if (drawn.isEmpty() && owned.isEmpty() && "退会した人".equals(who.name())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("who", who);
        model.addAttribute("drawn", drawn);
        model.addAttribute("owned", owned);
        return "auction/user";
    }

    /** 収集家の一覧 */
    @GetMapping("/auction/collectors")
    public String collectors(Model model) {
        addDays(model);
        model.addAttribute("collectors", ArtCollectors.ALL);
        return "auction/collectors";
    }

    /** 収集家1人のページ：好み・持っている絵 */
    @GetMapping("/auction/collectors/{key:[a-z]+}")
    public String collector(@PathVariable String key, Model model) {
        ArtCollector c = ArtCollectors.find(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        addDays(model);
        model.addAttribute("collector", c);
        model.addAttribute("who", AuctionViews.npc(key));
        model.addAttribute("owned", views.ownedByCollector(key));
        return "auction/collector";
    }

    /** ランキング：資産・画家・名画 */
    @GetMapping("/auction/ranking")
    public String ranking(Model model) {
        model.addAttribute("assets", views.assets(20));
        model.addAttribute("artists", views.artists(20));
        model.addAttribute("masterpieces", views.masterpieces(12));
        return "auction/ranking";
    }

    /** 遊び方と利用規約 */
    @GetMapping("/auction/rules")
    public String rules(Model model) {
        model.addAttribute("startCoins", coins.getStartCoins());
        model.addAttribute("dailyBonus", coins.getDailyBonus());
        model.addAttribute("dailyLimit", artworks.getDailyLimit());
        model.addAttribute("collectors", ArtCollectors.ALL);
        model.addAttribute("trends", AuctionDay.TRENDS);
        model.addAttribute("lengths", Length.values());
        return "auction/rules";
    }
}
