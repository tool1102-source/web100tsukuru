package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.dto.FictionForm;
import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.service.FictionService;
import com.example.web100tsukuru.service.PostRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class FictionController {

    private final FictionService fictionService;
    private final PostRateLimiter rateLimiter;

    public FictionController(FictionService fictionService, PostRateLimiter rateLimiter) {
        this.fictionService = fictionService;
        this.rateLimiter = rateLimiter;
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
        return "fiction/list";
    }

    /** 投稿詳細 (GET /fiction/{id}) */
    @GetMapping("/fiction/{id:\\d+}")
    public String show(@PathVariable Long id, Model model) {
        Fiction fiction = fictionService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("fiction", fiction);
        return "fiction/show";
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
                                HttpServletRequest request,
                                HttpServletResponse response,
                                Model model) {

        // おとり欄に入力がある＝ボット。保存せず、成功したように見せて一覧へ
        if (form.isFilledByBot()) {
            return "redirect:/fiction";
        }

        if (bindingResult.hasErrors()) {
            return "fiction/new";
        }

        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("rateLimited", true);
            return "fiction/new";
        }

        Fiction saved = fictionService.saveFiction(form);
        return "redirect:/fiction/" + saved.getId();
    }
}
