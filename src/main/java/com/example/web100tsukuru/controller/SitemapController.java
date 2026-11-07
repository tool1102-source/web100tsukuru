package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.service.FictionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;

/** 検索エンジン向けのサイトマップ（Google Search Console に登録する） */
@RestController
public class SitemapController {

    private static final int MAX_POSTS = 5000;

    private final FictionService fictionService;
    private final String baseUrl;

    public SitemapController(FictionService fictionService, @Value("${app.base-url}") String baseUrl) {
        this.fictionService = fictionService;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(xml, "/", null);
        url(xml, "/fiction", null);
        for (Fiction f : fictionService.findLatest(MAX_POSTS)) {
            url(xml, "/fiction/" + f.getId(), f.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        return xml.append("</urlset>\n").toString();
    }

    private void url(StringBuilder xml, String path, String lastmod) {
        xml.append("  <url><loc>").append(baseUrl).append(path).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(lastmod).append("</lastmod>");
        }
        xml.append("</url>\n");
    }
}
