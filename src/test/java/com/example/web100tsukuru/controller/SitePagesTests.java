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
                .andExpect(content().string(containsString("<strong>003</strong><span>/ 100</span>")))
                .andExpect(content().string(containsString("のこり <b>97</b> 個")))
                .andExpect(content().string(containsString("<meta name=\"viewport\"")))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"https://web100tsukuru.com/\">")))
                .andExpect(content().string(containsString("og:title")))
                .andExpect(content().string(containsString("adsbygoogle")))
                .andReturn().getResponse().getContentAsString();

        // 100マス：作った3つはリンク、次の 004 に印、残りは空
        assertThat(html.split("class=\"tile\\s*tile-done\"", -1).length - 1).isEqualTo(3);
        assertThat(html.split("class=\"tile\\s*tile-next\"", -1).length - 1).isEqualTo(1);
        assertThat(html.split("class=\"tile\\s*tile-empty\"", -1).length - 1).isEqualTo(96);
        assertThat(html).containsPattern("tile-next\">\\s*<span title=\"次に作るサイト\">004</span>");
        assertThat(html).contains("href=\"/vote\"");
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
        // いちばん新しい1本は大きく出し、残りはカードで並べる
        assertThat(html).contains("いちばん新しい話", "新着の体験談");
        assertThat(html.split("class=\"story-card\"", -1).length - 1).isEqualTo(1);
        // 記事の下には、ほかの投稿が出る（自分自身は出ない）
        Long oldId = repository.findAll().stream().filter(f -> f.getTitle().equals("古い話")).findFirst().orElseThrow().getId();
        String article = mvc.perform(get("/fiction/" + oldId)).andReturn().getResponse().getContentAsString();
        assertThat(article).contains("ほかの体験談", "新しい話");
        assertThat(article.split("class=\"story-card\"", -1).length - 1).isEqualTo(1);
    }

    @Test
    void 投稿がないときは書き方の例を出す() throws Exception {
        mvc.perform(get("/fiction"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("まだ投稿がありません。")))
                .andExpect(content().string(containsString("100円玉だけで1か月暮らしてみた")));
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
                .andExpect(content().string(containsString("name=\"website\"")))
                .andExpect(content().string(not(containsString("adsbygoogle"))));
    }

    @Test
    void プライバシーポリシーと投票所の利用規約がどのページからも行ける() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Google AdSense")))
                .andExpect(content().string(containsString("Google の名前・メールアドレス・写真は保存しません")));
        mvc.perform(get("/vote/terms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("お金やほかの物と交換できません")));
        mvc.perform(get("/fiction"))
                .andExpect(content().string(containsString("href=\"/privacy\"")))
                .andExpect(content().string(containsString("href=\"/vote/terms\"")));
    }

    @Test
    void ログインと登録の画面にGoogleでログインのボタンが出る() throws Exception {
        for (String page : new String[]{"/vote/login", "/vote/signup"}) {
            mvc.perform(get(page))
                    .andExpect(content().string(containsString("data-client_id=\"24928343678-rbgpibc53cefv1fkesoajk3resf5g2r1.apps.googleusercontent.com\"")))
                    .andExpect(content().string(containsString("https://accounts.google.com/gsi/client")));
        }
    }

    @Test
    void サイトごとに見出しと書体が分かれ_中のサイトからはトップへ戻れる() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("class=\"site-hub\"")))
                .andExpect(content().string(containsString("class=\"site-header\"")))
                .andExpect(content().string(containsString("family=Space+Grotesk")))
                .andExpect(content().string(not(containsString("class=\"hub-strip\""))));
        mvc.perform(get("/fiction"))
                .andExpect(content().string(containsString("class=\"site-fiction\"")))
                .andExpect(content().string(containsString("class=\"fiction-bar\"")))
                .andExpect(content().string(containsString("family=Dela+Gothic+One")))
                .andExpect(content().string(containsString("No.002 / 100")))
                .andExpect(content().string(not(containsString("class=\"site-header\""))));
        mvc.perform(get("/vote"))
                .andExpect(content().string(containsString("class=\"site-vote\"")))
                .andExpect(content().string(containsString("class=\"vote-bar\"")))
                .andExpect(content().string(not(containsString("fonts.googleapis.com"))))
                .andExpect(content().string(containsString("No.003 / 100")));
        // エラー画面は、もともと開こうとしたページのサイトに合わせる（MockMvc はエラー画面を描かないので、判定だけ確かめる）
        assertThat(GlobalModelAttributes.siteOf("/fiction/99999")).isEqualTo("fiction");
        assertThat(GlobalModelAttributes.siteOf("/vote/1/report")).isEqualTo("vote");
        assertThat(GlobalModelAttributes.siteOf("/fictionx")).isEqualTo("hub");
        assertThat(GlobalModelAttributes.siteOf("/admin/vote")).isEqualTo("hub");
    }

    @Test
    void 合言葉が未設定なら管理画面は無い() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isNotFound());
        mvc.perform(post("/admin/login").param("password", "")).andExpect(status().isNotFound());
        mvc.perform(post("/admin/delete").param("ids", "1")).andExpect(status().isNotFound());
    }
}
