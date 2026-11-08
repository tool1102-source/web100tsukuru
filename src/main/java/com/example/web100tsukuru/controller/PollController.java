package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.Poll;
import com.example.web100tsukuru.service.FollowService;
import com.example.web100tsukuru.service.ImageProcessor;
import com.example.web100tsukuru.service.ImageProcessor.InvalidImageException;
import com.example.web100tsukuru.service.ImageProcessor.ProcessedImage;
import com.example.web100tsukuru.service.PollService;
import com.example.web100tsukuru.service.PollService.PollDuration;
import com.example.web100tsukuru.service.PollService.ReportResult;
import com.example.web100tsukuru.service.PollService.VoteResult;
import com.example.web100tsukuru.service.PostRateLimiter;
import com.example.web100tsukuru.service.VoteLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** No.003「ちいさな投票所」 */
@Controller
public class PollController {

    public static final int TITLE_MAX = 60;
    public static final int DETAIL_MAX = 500;
    public static final int OPTION_MAX = 30;
    public static final int REPORT_MAX = 1000;

    private final PollService polls;
    private final FollowService follows;
    private final VoteLogin login;
    private final ImageProcessor imageProcessor;
    // 選挙の開きすぎ・投票のしすぎを止める（同じ接続元から 10分あたり）
    private final PostRateLimiter createLimiter;
    private final PostRateLimiter voteLimiter;

    public PollController(PollService polls, FollowService follows, VoteLogin login, ImageProcessor imageProcessor,
                          @Value("${app.vote.create-limit:5}") int createLimit,
                          @Value("${app.vote.vote-limit:120}") int voteLimit) {
        this.polls = polls;
        this.follows = follows;
        this.login = login;
        this.imageProcessor = imageProcessor;
        this.createLimiter = new PostRateLimiter(createLimit, Duration.ofMinutes(10));
        this.voteLimiter = new PostRateLimiter(voteLimit, Duration.ofMinutes(10));
    }

    /** 投票所のトップ：ホーム（おすすめ）・フォロー中・受付中・開票済み (GET /vote) */
    @GetMapping("/vote")
    public String list(@RequestParam(defaultValue = "all") String tab, HttpServletRequest request, Model model) {
        // 左の切り替え（note のホームのような形）：ホーム / フォロー中 / 受付中 / 開票済み / 報告あり
        String current = switch (tab) {
            case "following", "open", "closed", "reported" -> tab;
            default -> "all";
        };
        Long userId = login.currentUserId(request);
        List<Long> followeeIds = userId == null ? List.of() : follows.followeeIds(userId);
        if (current.equals("following")) {
            // フォローしている人が開催した選挙（新しい順）。ログインしていなければ、画面でログインをすすめる
            model.addAttribute("tab", current);
            model.addAttribute("followsAnyone", !followeeIds.isEmpty());
            model.addAttribute("followed", polls.hostedBy(followeeIds, 60));
            return "vote/list";
        }
        // 受付中は60件まで読み、「すべて」では締め切りが近い12件だけ並べる（順位は60件の中から選ぶ）
        List<PollService.PollSummary> openAll = polls.openPolls(60);
        List<PollService.PollSummary> closed = polls.closedPolls(current.equals("all") ? 8 : 60);
        model.addAttribute("tab", current);
        model.addAttribute("open", current.equals("open") ? openAll : openAll.subList(0, Math.min(12, openAll.size())));
        model.addAttribute("closed", current.equals("reported")
                ? closed.stream().filter(s -> s.poll().isReported()).toList()
                : closed);
        if (current.equals("all")) {
            // ホームの「あなたへのおすすめ」：フォローしている人の選挙と、フォローしていない人のおすすめを混ぜる
            model.addAttribute("feed", polls.feed(userId, followeeIds, 12));
        }
        // 「注目の選挙」：受付中のうち、票の多い順に5件
        model.addAttribute("hot", openAll.stream()
                .filter(s -> s.totalTickets() > 0)
                .sorted((a, b) -> Long.compare(b.totalTickets(), a.totalTickets()))
                .limit(5)
                .toList());
        return "vote/list";
    }

    /** 選挙を開催するフォーム (GET /vote/new)。ログインが要る */
    @GetMapping("/vote/new")
    public String newForm(HttpServletRequest request, Model model) {
        if (login.currentUserId(request) == null) {
            return "redirect:/vote/login?next=/vote/new";
        }
        model.addAttribute("durations", PollDuration.values());
        model.addAttribute("duration", PollDuration.DAY_1.name());
        return "vote/new";
    }

    /** 選挙を受け取って保存する (POST /vote) */
    @PostMapping("/vote")
    public String create(@RequestParam(defaultValue = "") String title,
                         @RequestParam(defaultValue = "") String detail,
                         @RequestParam(name = "option", required = false) List<String> optionInputs,
                         @RequestParam(defaultValue = "DAY_1") String duration,
                         @RequestParam(defaultValue = "") String website,
                         @RequestParam(name = "image", required = false) MultipartFile image,
                         HttpServletRequest request, HttpServletResponse response, Model model) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return "redirect:/vote/login?next=/vote/new";
        }
        if (!website.isBlank()) {
            return "redirect:/vote";
        }
        List<String> labels = (optionInputs == null ? List.<String>of() : optionInputs).stream()
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
        model.addAttribute("pollTitle", title);
        model.addAttribute("pollDetail", detail);
        model.addAttribute("options", optionInputs == null ? List.of() : optionInputs);
        model.addAttribute("duration", duration);
        model.addAttribute("durations", PollDuration.values());

        String error = validate(title, detail, labels, duration);
        if (error != null) {
            model.addAttribute("error", error);
            return "vote/new";
        }
        if (!createLimiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("error", "短い時間に続けて選挙が開催されたため、受け付けを一時的に止めています。10分ほどたってから、もう一度お試しください。");
            return "vote/new";
        }
        ProcessedImage processed = null;
        if (image != null && !image.isEmpty()) {
            try {
                processed = imageProcessor.process(image);
            } catch (InvalidImageException e) {
                model.addAttribute("error", e.getMessage());
                return "vote/new";
            }
        }
        Poll poll = polls.create(userId, title, detail, labels, PollDuration.valueOf(duration), processed);
        return "redirect:/vote/" + poll.getId();
    }

    /** 選挙のページ (GET /vote/{id}) */
    @GetMapping("/vote/{id:\\d+}")
    public String show(@PathVariable Long id, HttpServletRequest request, Model model) {
        Long userId = login.currentUserId(request);
        PollService.PollView view = polls.view(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("view", view);
        model.addAttribute("poll", view.poll());
        return "vote/show";
    }

    /** 投票券を入れる (POST /vote/{id}/tickets) */
    @PostMapping("/vote/{id:\\d+}/tickets")
    public String vote(@PathVariable Long id, @RequestParam Long optionId, @RequestParam(defaultValue = "0") String tickets,
                       HttpServletRequest request, RedirectAttributes redirect) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return "redirect:/vote/login?next=/vote/" + id;
        }
        if (!voteLimiter.tryAcquire(request.getRemoteAddr())) {
            redirect.addFlashAttribute("error", "短い時間に続けて投票されたため、受け付けを一時的に止めています。10分ほどたってから、もう一度お試しください。");
            return "redirect:/vote/" + id;
        }
        long count;
        try {
            count = Long.parseLong(tickets.strip());
        } catch (NumberFormatException e) {
            count = 0;
        }
        VoteResult result = polls.vote(id, optionId, userId, count);
        switch (result) {
            case OK -> redirect.addFlashAttribute("notice", count + "票を投票しました。");
            case CLOSED -> redirect.addFlashAttribute("error", "この選挙の投票は締め切られました。");
            case NOT_ENOUGH -> redirect.addFlashAttribute("error", "投票券が足りません。");
            case INVALID -> redirect.addFlashAttribute("error", "投票数は1票以上で入力してください。");
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:/vote/" + id + "#options";
    }

    /** 報告のフォーム (GET /vote/{id}/report)。出した人だけ・締め切りのあと */
    @GetMapping("/vote/{id:\\d+}/report")
    public String reportForm(@PathVariable Long id, HttpServletRequest request, Model model) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return "redirect:/vote/login?next=/vote/" + id + "/report";
        }
        PollService.PollView view = polls.view(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!view.owner() || view.poll().isOpen() || view.poll().isReported()) {
            return "redirect:/vote/" + id;
        }
        model.addAttribute("view", view);
        model.addAttribute("poll", view.poll());
        return "vote/report";
    }

    /** 報告を受け取る (POST /vote/{id}/report) */
    @PostMapping("/vote/{id:\\d+}/report")
    public String report(@PathVariable Long id, @RequestParam(defaultValue = "") String text,
                         @RequestParam(name = "images", required = false) List<MultipartFile> images,
                         HttpServletRequest request, Model model, RedirectAttributes redirect) {
        Long userId = login.currentUserId(request);
        if (userId == null) {
            return "redirect:/vote/login?next=/vote/" + id + "/report";
        }
        PollService.PollView view = polls.view(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("view", view);
        model.addAttribute("poll", view.poll());
        model.addAttribute("text", text);
        List<MultipartFile> files = images == null ? List.of() : images.stream().filter(f -> !f.isEmpty()).toList();
        if (text.isBlank() || text.strip().length() > REPORT_MAX) {
            model.addAttribute("error", "報告は1〜" + REPORT_MAX + "文字で書いてください。");
            return "vote/report";
        }
        if (files.size() > PollService.MAX_REPORT_IMAGES) {
            model.addAttribute("error", "写真は" + PollService.MAX_REPORT_IMAGES + "枚までです。");
            return "vote/report";
        }
        List<ProcessedImage> processed = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                processed.add(imageProcessor.process(file));
            } catch (InvalidImageException e) {
                model.addAttribute("error", e.getMessage());
                return "vote/report";
            }
        }
        ReportResult result = polls.report(id, userId, text, processed);
        if (result == ReportResult.OK) {
            redirect.addFlashAttribute("notice", "実行報告を載せました。");
        }
        return "redirect:/vote/" + id;
    }

    /** 選挙の中身を確かめる。問題がなければ null */
    private static String validate(String title, String detail, List<String> labels, String duration) {
        if (title.isBlank() || title.strip().length() > TITLE_MAX) {
            return "タイトルは1〜" + TITLE_MAX + "文字で入力してください。";
        }
        if (detail.strip().length() > DETAIL_MAX) {
            return "説明は" + DETAIL_MAX + "文字までです。";
        }
        if (labels.size() < 2 || labels.size() > 4) {
            return "候補は2〜4個にしてください。";
        }
        Set<String> seen = new HashSet<>();
        for (String label : labels) {
            if (label.length() > OPTION_MAX) {
                return "候補は1つ" + OPTION_MAX + "文字までです。";
            }
            if (!seen.add(label)) {
                return "同じ候補が2つあります。";
            }
        }
        try {
            PollDuration.valueOf(duration);
        } catch (IllegalArgumentException e) {
            return "締め切りを選んでください。";
        }
        return null;
    }
}
