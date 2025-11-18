package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.dto.FictionForm;
import com.example.web100tsukuru.service.FictionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class FictionController {

    private final FictionService fictionService;

    public FictionController(FictionService fictionService) {
        this.fictionService = fictionService;
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

        fictionService.saveFiction(form);

        // 投稿が成功したらトップページにリダイレクト
        return "redirect:/";
    }
}