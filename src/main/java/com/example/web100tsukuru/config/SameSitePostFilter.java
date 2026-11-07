package com.example.web100tsukuru.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

/**
 * よそのサイトから送られてきた POST（投票・投稿・削除など）を断る。
 *
 * ログイン状態の Cookie は SameSite なので、よそのサイトからの POST にはもともと付かない。
 * それでも古いブラウザなどに備え、ブラウザが付ける「どこから来たか」（Sec-Fetch-Site / Origin）も確かめる。
 * どちらも付いていなければ（古いブラウザ・テストなど）通す。
 */
@Component
public class SameSitePostFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && fromAnotherSite(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean fromAnotherSite(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null) {
            return fetchSite.equals("cross-site");
        }
        String origin = request.getHeader("Origin");
        if (origin == null || origin.equals("null")) {
            return origin != null;
        }
        try {
            String host = URI.create(origin).getHost();
            return host == null || !host.equalsIgnoreCase(request.getServerName());
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
