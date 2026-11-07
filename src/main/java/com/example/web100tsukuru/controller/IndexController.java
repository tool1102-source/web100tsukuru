package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.site.WebsiteCatalog;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    private final WebsiteCatalog catalog;

    public IndexController(WebsiteCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("catalog", catalog);
        return "index";
    }
}
