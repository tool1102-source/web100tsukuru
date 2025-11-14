package com.example.web100tsukuru.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    /**
     * ルートURL (/) へのアクセスを受け付け、
     * templates/index.html (テンプレート名: "index") を返す
     */
    @GetMapping("/")
    public String index() {
        // Thymeleafに "index.html" をレンダリングするように指示
        return "index"; 
    }
}