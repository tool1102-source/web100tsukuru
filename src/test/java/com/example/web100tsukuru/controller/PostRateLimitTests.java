package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.repository.FictionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.post-limit.max=2")
@AutoConfigureMockMvc
class PostRateLimitTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    FictionRepository repository;

    @Test
    void 短時間に投稿が続くと止める() throws Exception {
        repository.deleteAll();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/fiction").param("title", "t" + i).param("authorName", "a").param("body", "b"))
                    .andExpect(status().is3xxRedirection());
        }
        mvc.perform(post("/fiction").param("title", "t3").param("authorName", "a").param("body", "b"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("10分ほどたってから")));
        assertThat(repository.count()).isEqualTo(2);
    }
}
