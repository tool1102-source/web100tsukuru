package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.Fiction;
import com.example.web100tsukuru.repository.FictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 「テスト用の合言葉」の SHA-256
@SpringBootTest(properties = "app.admin-password-hash=eb612e81bc1519b28a127b8112ca3d90dce0c1c13a99629655e38d707659ab37")
@AutoConfigureMockMvc
class AdminTests {

    private static final String PASSWORD = "テスト用の合言葉";
    // 合言葉の試行回数は接続元ごとに数えるので、テストごとに別の接続元にする
    private static final AtomicInteger CLIENT = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    FictionRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void ログインしないと投稿の一覧は見えず_消せない() throws Exception {
        Fiction f = save("消されてはいけない話");
        mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("合言葉を入れてください")))
                .andExpect(content().string(not(containsString("消されてはいけない話"))));
        mvc.perform(post("/admin/delete").param("ids", f.getId().toString()))
                .andExpect(redirectedUrl("/admin"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void 合言葉が違うと入れない() throws Exception {
        mvc.perform(from(post("/admin/login").param("password", "ちがう")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("合言葉が違います")));
        mvc.perform(from(post("/admin/login")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 合言葉で入ると_チェックした投稿だけ消せる() throws Exception {
        Fiction spam = save("不適切な話");
        Fiction keep = save("残す話");
        MockHttpSession session = login();

        mvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("不適切な話")))
                .andExpect(content().string(containsString("残す話")))
                .andExpect(content().string(containsString("投稿は全部で 2 件")));

        mvc.perform(post("/admin/delete").session(session).param("ids", spam.getId().toString(), "99999"))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("deletedCount", 1));
        assertThat(repository.findAll()).extracting(Fiction::getId).containsExactly(keep.getId());

        mvc.perform(get("/admin").session(session).flashAttr("deletedCount", 1))
                .andExpect(content().string(containsString("1件の投稿を消しました。")));
    }

    @Test
    void 何も選ばずに押しても何も消えない() throws Exception {
        save("話");
        mvc.perform(post("/admin/delete").session(login()))
                .andExpect(flash().attribute("deletedCount", 0));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void ログアウトすると消せなくなる() throws Exception {
        Fiction f = save("話");
        MockHttpSession session = login();
        mvc.perform(post("/admin/logout").session(session)).andExpect(redirectedUrl("/admin"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(post("/admin/delete").param("ids", f.getId().toString()))
                .andExpect(redirectedUrl("/admin"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void 合言葉を短い時間に何度も試すと止める() throws Exception {
        String client = "10.9.9.9";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/admin/login").param("password", "ちがう" + i).with(r -> { r.setRemoteAddr(client); return r; }))
                    .andExpect(status().isUnauthorized());
        }
        // 6回目は、正しい合言葉でも受け付けない
        mvc.perform(post("/admin/login").param("password", PASSWORD).with(r -> { r.setRemoteAddr(client); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("10分ほどたってから")));
    }

    @Test
    void 管理画面は検索結果に出さず_広告も載せない() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(not(containsString("adsbygoogle"))));
        mvc.perform(get("/admin").session(login()))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(not(containsString("adsbygoogle"))));
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(from(post("/admin/login").param("password", PASSWORD)))
                .andExpect(redirectedUrl("/admin"))
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request) {
        String client = "10.0.0." + CLIENT.incrementAndGet();
        return request.with(r -> { r.setRemoteAddr(client); return r; });
    }

    private Fiction save(String title) {
        Fiction f = new Fiction();
        f.setTitle(title);
        f.setAuthorName("a");
        f.setBody("本文");
        return repository.save(f);
    }
}
