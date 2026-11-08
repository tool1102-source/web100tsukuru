package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.dto.FictionForm;
import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.entity.FictionImage;
import com.example.web100tsukuru.service.FictionService;
import com.example.web100tsukuru.service.ImageProcessor;
import com.example.web100tsukuru.service.ImageProcessor.InvalidImageException;
import com.example.web100tsukuru.service.ImageProcessor.ProcessedImage;
import com.example.web100tsukuru.service.PostRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Controller
public class FictionController {

    private final FictionService fictionService;
    private final PostRateLimiter rateLimiter;
    private final ImageProcessor imageProcessor;

    public FictionController(FictionService fictionService, PostRateLimiter rateLimiter, ImageProcessor imageProcessor) {
        this.fictionService = fictionService;
        this.rateLimiter = rateLimiter;
        this.imageProcessor = imageProcessor;
    }

    /** 投稿一覧 (GET /fiction?page=2) */
    @GetMapping("/fiction")
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        Page<Fiction> fictions = fictionService.findPage(page);
        if (page < 1 || (page > 1 && page > fictions.getTotalPages())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("fictions", fictions);
        model.addAttribute("page", page);
        // カードのサムネイルには、それぞれの1枚目の画像を使う（画像のない投稿は題名を大きく書く）
        model.addAttribute("images", fictionService.imageNumbers(fictions.map(Fiction::getId).toList()));
        return "fiction/list";
    }

    /** 投稿詳細 (GET /fiction/{id}) */
    @GetMapping("/fiction/{id:\\d+}")
    public String show(@PathVariable Long id, Model model) {
        Fiction fiction = fictionService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("fiction", fiction);
        model.addAttribute("blocks", fictionService.articleBlocks(fiction));
        // 記事の下に「ほかの体験談」を新しい順に3件
        List<Fiction> more = fictionService.findLatest(4).stream()
                .filter(f -> !f.getId().equals(id))
                .limit(3)
                .toList();
        model.addAttribute("more", more);
        model.addAttribute("images", fictionService.imageNumbers(more.stream().map(Fiction::getId).toList()));
        return "fiction/show";
    }

    /**
     * 投稿の画像 (GET /fiction/{id}/images/{no}.jpg)。
     * 見た人のブラウザには1日覚えさせるが、途中の中継サーバーには覚えさせない
     * （管理画面で消した不適切な画像が、中継サーバーから配られ続けないように）。
     */
    @GetMapping("/fiction/{id:\\d+}/images/{no:\\d+}.jpg")
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable int no) {
        FictionImage image = fictionService.findImage(id, no)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.getData());
    }

    /** 投稿フォーム (GET /fiction/new) */
    @GetMapping("/fiction/new")
    public String showForm(Model model) {
        model.addAttribute("fictionForm", new FictionForm());
        return "fiction/new";
    }

    /** 投稿の受け取りと保存 (POST /fiction) */
    @PostMapping("/fiction")
    public String createFiction(@Valid @ModelAttribute("fictionForm") FictionForm form,
                                BindingResult bindingResult,
                                @RequestParam(name = "images", required = false) List<MultipartFile> images,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                Model model) {

        // おとり欄に入力がある＝ボット。保存せず、成功したように見せて一覧へ
        if (form.isFilledByBot()) {
            return "redirect:/fiction";
        }

        // 画像を選ばなかったときも、空のファイル欄が1つ届くので除く
        List<MultipartFile> files = images == null ? List.of()
                : images.stream().filter(f -> !f.isEmpty()).toList();
        // 画面を出し直すと選んだ画像は消えるので、選び直してもらう
        model.addAttribute("imagesDropped", !files.isEmpty());

        if (bindingResult.hasErrors()) {
            return "fiction/new";
        }
        if (files.size() > FictionService.MAX_IMAGES) {
            model.addAttribute("imageError", "画像は" + FictionService.MAX_IMAGES + "枚までです。");
            return "fiction/new";
        }

        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("rateLimited", true);
            return "fiction/new";
        }

        List<ProcessedImage> processed = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                processed.add(imageProcessor.process(file));
            } catch (InvalidImageException e) {
                model.addAttribute("imageError", e.getMessage());
                return "fiction/new";
            }
        }

        Fiction saved = fictionService.saveFiction(form, processed);
        return "redirect:/fiction/" + saved.getId();
    }
}
