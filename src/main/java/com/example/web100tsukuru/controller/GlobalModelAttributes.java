package com.example.web100tsukuru.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** すべての画面で使う値（canonical URL や OGP 用・どのサイトにいるか） */
@ControllerAdvice
public class GlobalModelAttributes {

    private final String baseUrl;

    public GlobalModelAttributes(@Value("${app.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @ModelAttribute("baseUrl")
    public String baseUrl() {
        return baseUrl;
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /**
     * いまどのサイトにいるか（見出し・色・書体をサイトごとに変えるため）。
     * hub = webサイト100個作る人のトップなど、fiction = No.002、vote = No.003、auction = No.004。
     * エラー画面では、もともと開こうとしたページのサイトに合わせる。
     */
    @ModelAttribute("site")
    public String site(HttpServletRequest request) {
        Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = original instanceof String s ? s : request.getRequestURI();
        return siteOf(path);
    }

    static String siteOf(String path) {
        if (path == null) {
            return "hub";
        }
        if (path.equals("/fiction") || path.startsWith("/fiction/")) {
            return "fiction";
        }
        if (path.equals("/vote") || path.startsWith("/vote/")) {
            return "vote";
        }
        if (path.equals("/auction") || path.startsWith("/auction/")) {
            return "auction";
        }
        return "hub";
    }
}
