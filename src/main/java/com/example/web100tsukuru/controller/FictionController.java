package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.dto.FictionForm;
import com.example.web100tsukuru.service.FictionService;
import jakarta.validation.Valid;
import com.example.web100tsukuru.entity.Fiction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class FictionController {

    private final FictionService fictionService;

    public FictionController(FictionService fictionService) {
        this.fictionService = fictionService;
    }

    /**
     * 投稿一覧 (GET /fiction)
     */
    @GetMapping("/fiction")
    public String list(Model model) {
        model.addAttribute("fictions", fictionService.findAll());
        return "fiction/list";
    }

    /**
     * 投稿詳細 (GET /fiction/{id})
     */
    @GetMapping("/fiction/{id:\\d+}")
    public String show(@PathVariable Long id, Model model) {
        Fiction fiction = fictionService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("fiction", fiction);
        model.addAttribute("bodyParts", fictionService.splitBody(fiction.getBody()));
        return "fiction/show";
    }

    /**
     * フィクション投稿フォームの表示 (GET /fiction/new)
     */
    @GetMapping("/fiction/new")
    public String showForm(Model model) {
        model.addAttribute("fictionForm", new FictionForm());
        return "fiction/new"; // templates/fiction/new.htmlを表示
    }

    /**
     * フィクション投稿データの受け取りと保存 (POST /fiction)
     */
    @PostMapping("/fiction")
    public String createFiction(@Valid FictionForm form, BindingResult bindingResult) {
        
        // 入力チェック (Validation) に引っかかった場合
        if (bindingResult.hasErrors()) {
            // エラーを添えてフォーム画面に戻す
            return "fiction/new";
        }

        Fiction saved = fictionService.saveFiction(form);

        // 投稿が成功したら、その投稿の詳細ページへ
        return "redirect:/fiction/" + saved.getId();
    }
}