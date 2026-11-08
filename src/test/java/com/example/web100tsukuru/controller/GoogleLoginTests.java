package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.PollImageRepository;
import com.example.web100tsukuru.repository.PollOptionRepository;
import com.example.web100tsukuru.repository.PollRepository;
import com.example.web100tsukuru.repository.PollVoteRepository;
import com.example.web100tsukuru.repository.TicketLedgerRepository;
import com.example.web100tsukuru.service.AccountService;
import com.example.web100tsukuru.service.GoogleIdTokens;
import com.example.web100tsukuru.service.GoogleIdTokens.GoogleIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 「Google でログイン」の流れ（証明書の確かめは GoogleIdTokensTests。ここでは結果だけを差し替える） */
@SpringBootTest
@AutoConfigureMockMvc
class GoogleLoginTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GoogleIdTokens google;

    @Autowired
    AppUserRepository users;

    @Autowired
    TicketLedgerRepository ledger;

    @Autowired
    PollRepository polls;

    @Autowired
    PollOptionRepository options;

    @Autowired
    PollVoteRepository votes;

    @Autowired
    PollImageRepository images;

    @Autowired
    AccountService accounts;

    @BeforeEach
    void clean() {
        images.deleteAll();
        votes.deleteAll();
        options.deleteAll();
        polls.deleteAll();
        ledger.deleteAll();
        users.deleteAll();
        when(google.verify(anyString())).thenReturn(Optional.empty());
        when(google.verify("good-token")).thenReturn(Optional.of(new GoogleIdentity("google-sub-1")));
    }

    @Test
    void はじめてGoogleで来た人は表示名を決めて登録し_投票券を100枚もらう() throws Exception {
        MvcResult first = mvc.perform(post("/vote/login/google").param("credential", "good-token").param("next", "/vote/new"))
                .andExpect(redirectedUrl("/vote/signup/google"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) first.getRequest().getSession(false);
        assertThat(users.count()).isZero();

        mvc.perform(get("/vote/signup/google").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("表示名を決める")))
                .andExpect(content().string(containsString("Google の名前は使いません")));

        mvc.perform(post("/vote/signup/google").session(session).param("displayName", "ぐーぐる太郎"))
                .andExpect(redirectedUrl("/vote/new"));

        AppUser user = users.findByGoogleSub("google-sub-1").orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("ぐーぐる太郎");
        assertThat(user.getTotalTickets()).isEqualTo(100);
        // パスワードではログインできない
        assertThat(accounts.authenticate(user.getLoginId(), "!google-login-only")).isEmpty();
        // そのままお題を出す画面に入れる
        mvc.perform(get("/vote/new").session(session)).andExpect(status().isOk());
    }

    @Test
    void 登録ずみの人はGoogleでそのままログインでき_アカウントは増えない() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/vote/login/google").param("credential", "good-token"))
                .andReturn().getRequest().getSession(false);
        mvc.perform(post("/vote/signup/google").session(session).param("displayName", "いちど目"));
        assertThat(users.count()).isEqualTo(1);

        MvcResult again = mvc.perform(post("/vote/login/google").param("credential", "good-token").param("next", "/vote/me"))
                .andExpect(redirectedUrl("/vote/me"))
                .andReturn();
        mvc.perform(get("/vote/me").cookie(again.getResponse().getCookie("vote_login")))
                .andExpect(content().string(containsString("いちど目 さん")));
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void 確かめられない証明書ではログインできない() throws Exception {
        mvc.perform(post("/vote/login/google").param("credential", "fake-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Google でのログインを確認できませんでした")));
        mvc.perform(get("/vote/signup/google")).andExpect(redirectedUrl("/vote/login"));
        mvc.perform(post("/vote/signup/google").param("displayName", "勝手に登録")).andExpect(redirectedUrl("/vote/login"));
        assertThat(users.count()).isZero();
    }
}
