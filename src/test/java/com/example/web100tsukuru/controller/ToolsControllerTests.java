package com.example.web100tsukuru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /tools/ の URL の結びつけ。
 * 本物の変換ツールは Dockerfile が入れるので、テストでは src/test/resources/static/tools/ の仮のページを使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ToolsControllerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void 末尾の斜線なしは付きへ転送する() throws Exception {
        mvc.perform(get("/tools")).andExpect(redirectedUrl("/tools/"));
    }

    @Test
    void トップと各ページを拡張子なしのURLで返す() throws Exception {
        mvc.perform(get("/tools/")).andExpect(status().isOk()).andExpect(forwardedUrl("/tools/index.html"));
        mvc.perform(get("/tools/date-tools")).andExpect(status().isOk()).andExpect(forwardedUrl("/tools/date-tools.html"));
    }

    @Test
    void ないページは404() throws Exception {
        mvc.perform(get("/tools/nope")).andExpect(status().isNotFound());
    }
}
