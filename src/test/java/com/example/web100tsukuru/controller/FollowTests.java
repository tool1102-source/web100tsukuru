package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.PollImageRepository;
import com.example.web100tsukuru.repository.PollOptionRepository;
import com.example.web100tsukuru.repository.PollRepository;
import com.example.web100tsukuru.repository.PollVoteRepository;
import com.example.web100tsukuru.repository.TicketLedgerRepository;
import com.example.web100tsukuru.repository.UserFollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 「ちいさな投票所」の人ごとのページとフォロー */
@SpringBootTest
@AutoConfigureMockMvc
class FollowTests {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PollRepository polls;

    @Autowired
    PollOptionRepository options;

    @Autowired
    PollVoteRepository votes;

    @Autowired
    PollImageRepository images;

    @Autowired
    TicketLedgerRepository ledger;

    @Autowired
    UserFollowRepository follows;

    @BeforeEach
    void clean() {
        follows.deleteAll();
        images.deleteAll();
        votes.deleteAll();
        options.deleteAll();
        polls.deleteAll();
        ledger.deleteAll();
        users.deleteAll();
    }

    @Test
    void フォローすると人数が増え_フォロー中にその人の選挙が並ぶ() throws Exception {
        MockHttpSession host = signup("host_secret", "しゅさい");
        MockHttpSession fan = signup("fan_secret", "ふぁん");
        Long hostId = users.findByLoginId("host_secret").orElseThrow().getId();
        Long pollId = createPoll(host);

        // 選挙のページの主催者の名前から、その人のページへ行ける
        mvc.perform(get("/vote/" + pollId))
                .andExpect(content().string(containsString("href=\"/vote/users/" + hostId + "\"")));
        // 人ごとのページには、表示名と開催した選挙が出る。ログイン ID は出さない
        mvc.perform(get("/vote/users/" + hostId).session(fan))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("しゅさい さん")))
                .andExpect(content().string(containsString("今日の選挙")))
                .andExpect(content().string(containsString("フォローする")))
                .andExpect(content().string(not(containsString("host_secret"))));

        mvc.perform(post("/vote/users/" + hostId + "/follow").session(fan))
                .andExpect(redirectedUrl("/vote/users/" + hostId));
        // 二度押しても1件のまま
        mvc.perform(post("/vote/users/" + hostId + "/follow").session(fan));
        assertThat(follows.count()).isEqualTo(1);

        mvc.perform(get("/vote/users/" + hostId).session(fan))
                .andExpect(content().string(containsString("しゅさい さんをフォローしました。")))
                .andExpect(content().string(containsString("フォロワー <b>1</b>人")))
                .andExpect(content().string(containsString("フォロー中")));
        mvc.perform(get("/vote").param("tab", "following").session(fan))
                .andExpect(content().string(containsString("フォロー中の人が開催した選挙")))
                .andExpect(content().string(containsString("今日の選挙")));
        // だれをフォローしているかは、本人のマイページにだけ出る
        mvc.perform(get("/vote/me").session(fan))
                .andExpect(content().string(containsString("フォロー <b>1</b>人")))
                .andExpect(content().string(containsString(">しゅさい</span>")));

        mvc.perform(post("/vote/users/" + hostId + "/unfollow").session(fan))
                .andExpect(redirectedUrl("/vote/users/" + hostId));
        assertThat(follows.count()).isZero();
        mvc.perform(get("/vote").param("tab", "following").session(fan))
                .andExpect(content().string(containsString("まだだれもフォローしていません。")))
                .andExpect(content().string(not(containsString("今日の選挙"))));
    }

    @Test
    void 自分はフォローできず_ログインしていないとフォローできない() throws Exception {
        MockHttpSession me = signup("self_id", "じぶん");
        Long myId = users.findByLoginId("self_id").orElseThrow().getId();

        mvc.perform(get("/vote/users/" + myId).session(me))
                .andExpect(content().string(containsString("マイページへ")))
                .andExpect(content().string(not(containsString("フォローする"))));
        mvc.perform(post("/vote/users/" + myId + "/follow").session(me))
                .andExpect(redirectedUrl("/vote/users/" + myId));
        mvc.perform(post("/vote/users/" + myId + "/follow"))
                .andExpect(redirectedUrl("/vote/login?next=/vote/users/" + myId));
        assertThat(follows.count()).isZero();

        mvc.perform(get("/vote/users/99999")).andExpect(status().isNotFound());
        mvc.perform(get("/vote").param("tab", "following"))
                .andExpect(content().string(containsString("すると、フォローしている人が開催した選挙がここに並びます。")));
    }

    private MockHttpSession signup(String loginId, String displayName) throws Exception {
        MvcResult result = mvc.perform(post("/vote/signup")
                        .param("loginId", loginId).param("displayName", displayName).param("password", "password123")
                        .with(r -> { r.setRemoteAddr("10.30.0." + SEQ.incrementAndGet()); return r; }))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long createPoll(MockHttpSession owner) throws Exception {
        String location = mvc.perform(post("/vote").session(owner)
                        .param("title", "今日の選挙").param("option", "A", "B").param("duration", "DAY_1"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }
}
