package com.example.web100tsukuru.controller;

import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * No.001 の変換ツール（web100tsukuru.com/tools/）。
 *
 * 中身は別リポジトリ（tool1102-source/spring-date-converter）の静的なファイルで、
 * Dockerfile が static/tools/ に書き出して入れる。
 * ここでは /tools/date-tools のような拡張子なしの URL を、date-tools.html に結びつけるだけ。
 */
@Controller
public class ToolsController {

    private final ResourceLoader resourceLoader;

    public ToolsController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GetMapping("/tools")
    public String withoutSlash() {
        return "redirect:/tools/";
    }

    @GetMapping("/tools/")
    public String index() {
        return page("index");
    }

    @GetMapping("/tools/{page:[a-z0-9-]+}")
    public String page(@PathVariable String page) {
        if (!resourceLoader.getResource("classpath:/static/tools/" + page + ".html").exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/tools/" + page + ".html";
    }
}
