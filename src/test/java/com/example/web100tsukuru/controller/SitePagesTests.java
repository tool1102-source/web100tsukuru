package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.repository.FictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SitePagesTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    FictionRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void トップページに作ったサイトと進み具合が出る() throws Exception {
        String html = mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ConverterTools")))
                .andExpect(content().string(containsString("href=\"/tools/\"")))
                .andExpect(content().string(not(containsString("convertertools.jp"))))
                .andExpect(content().string(containsString("href=\"/fiction\"")))
                .andExpect(content().string(containsString("<strong>002</strong><span>/ 100</span>")))
                .andExpect(content().string(containsString("のこり <b>98</b> 個")))
                .andExpect(content().string(containsString("<meta name=\"viewport\"")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://web100tsukuru.com/\">")))
                .andExpect(content().string(containsString("og:title")))
                .andReturn().getResponse().getContentAsString();

        // 100マス：作った2つはリンク、次の 003 に印、残りは空
        assertThat(html.split("class=\"tile\\s*tile-done\"", -1).length - 1).isEqualTo(2);
        assertThat(html.split("class=\"tile\\s*tile-next\"", -1).length - 1).isEqualTo(1);
        assertThat(html.split("class=\"tile\\s*tile-empty\"", -1).length - 1).isEqualTo(97);
        assertThat(html).containsPattern("tile-next\">\\s*<span title=\"次に作るサイト\">003</span>");
    }

    @Test
    void 投稿すると詳細ページに移り_IMGの目印は表示されない() throws Exception {
        MvcResult result = mvc.perform(post("/fiction")
                        .param("title", "電車で一輪車をこいでみた話")
                        .param("authorName", "テスト")
                        .param("body", "一行目[IMG]\n二行目"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/fiction/*"))
                .andReturn();

        mvc.perform(get(result.getResponse().getRedirectedUrl()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("電車で一輪車をこいでみた話")))
                .andExpect(content().string(containsString("一行目\n二行目")))
                .andExpect(content().string(not(containsString("[IMG]"))))
                .andExpect(content().string(not(containsString("準備中"))));

        Fiction saved = repository.findAll().get(0);
        assertThat(saved.getPlaceholderCount()).isEqualTo(1);
        // 投稿日時は日本時間で記録される
        assertThat(Duration.between(saved.getCreatedAt(), LocalDateTime.now(Web100tsukuruApplication.ZONE)).abs())
                .isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void 未入力だとエラーを出して保存しない() throws Exception {
        mvc.perform(post("/fiction").param("title", " ").param("authorName", "").param("body", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("タイトルを入力してください。")))
                .andExpect(content().string(containsString("本文を入力してください。")));
        assertThat(repository.count()).isZero();
    }

    @Test
    void おとり欄に入力があるボット投稿は保存しない() throws Exception {
        mvc.perform(post("/fiction")
                        .param("title", "spam").param("authorName", "bot").param("body", "buy now")
                        .param("website", "http://spam.example"))
                .andExpect(redirectedUrl("/fiction"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void 一覧に投稿が新しい順で出る() throws Exception {
        for (String title : new String[]{"古い話", "新しい話"}) {
            mvc.perform(post("/fiction").param("title", title).param("authorName", "a").param("body", "本文"));
        }
        String html = mvc.perform(get("/fiction")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html.indexOf("新しい話")).isLessThan(html.indexOf("古い話"));
    }

    @Test
    void 存在しないページは日本語の404画面になる() throws Exception {
        mvc.perform(get("/fiction/99999"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/fiction?page=5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void サイトマップに投稿のURLが入る() throws Exception {
        mvc.perform(post("/fiction").param("title", "t").param("authorName", "a").param("body", "b"));
        Long id = repository.findAll().get(0).getId();
        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<loc>https://web100tsukuru.com/</loc>")))
                .andExpect(content().string(containsString("<loc>https://web100tsukuru.com/fiction/" + id + "</loc>")));
    }

    @Test
    void robotsとadsのファイルが出る() throws Exception {
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sitemap: https://web100tsukuru.com/sitemap.xml")))
                .andExpect(content().string(containsString("Sitemap: https://web100tsukuru.com/tools/sitemap.xml")));
        mvc.perform(get("/ads.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pub-4550031417169253")));
    }

    @Test
    void 投稿フォームは検索結果に出さない() throws Exception {
        mvc.perform(get("/fiction/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString("name=\"website\"")));
    }
}
