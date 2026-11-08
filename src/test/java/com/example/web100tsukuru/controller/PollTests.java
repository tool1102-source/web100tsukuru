package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.Web100tsukuruApplication;
import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.Poll;
import com.example.web100tsukuru.entity.PollOption;
import com.example.web100tsukuru.entity.TicketLedger;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.PollImageRepository;
import com.example.web100tsukuru.repository.PollOptionRepository;
import com.example.web100tsukuru.repository.PollRepositories.OptionTally;
import com.example.web100tsukuru.repository.PollRepository;
import com.example.web100tsukuru.repository.PollVoteRepository;
import com.example.web100tsukuru.repository.TicketLedgerRepository;
import com.example.web100tsukuru.service.PollService;
import com.example.web100tsukuru.service.PollSettlement;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PollTests {

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
    PollService pollService;

    @BeforeEach
    void clean() {
        images.deleteAll();
        votes.deleteAll();
        options.deleteAll();
        polls.deleteAll();
        ledger.deleteAll();
        users.deleteAll();
    }

    @Test
    void 登録すると投票券を100枚もらえ_同じ日はボーナスが出ない() throws Exception {
        MockHttpSession session = signup("hanako");
        AppUser user = users.findByLoginId("hanako").orElseThrow();
        assertThat(user.getTotalTickets()).isEqualTo(100);
        mvc.perform(get("/vote").session(session))
                .andExpect(content().string(containsString("<b>100</b>枚")))
                .andExpect(content().string(not(containsString("ログインボーナス"))));
        assertThat(users.findById(user.getId()).orElseThrow().getTotalTickets()).isEqualTo(100);
    }

    @Test
    void 日が変わって開くとログインボーナスで10枚増える_1日1回だけ() throws Exception {
        MockHttpSession session = signup("taro");
        AppUser user = users.findByLoginId("taro").orElseThrow();
        user.setLastBonusDate(LocalDate.now(Web100tsukuruApplication.ZONE).minusDays(1));
        users.save(user);

        mvc.perform(get("/vote").session(session))
                .andExpect(content().string(containsString("ログインボーナスとして、投票券を10枚受け取りました。")));
        mvc.perform(get("/vote").session(session))
                .andExpect(content().string(not(containsString("ログインボーナスとして"))));
        assertThat(users.findById(user.getId()).orElseThrow().getTotalTickets()).isEqualTo(110);
    }

    @Test
    void 同じログインIDでは登録できず_パスワードが違うとログインできない() throws Exception {
        signup("same_id");
        mvc.perform(post("/vote/signup").param("loginId", "same_id").param("displayName", "別の人").param("password", "password123")
                        .with(r -> { r.setRemoteAddr(nextIp()); return r; }))
                .andExpect(content().string(containsString("そのログイン ID は、もう使われています。")));
        mvc.perform(post("/vote/login").param("loginId", "same_id").param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("ログイン ID かパスワードが違います。")));
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void ログインしないとお題を出せず_券も入れられない() throws Exception {
        mvc.perform(get("/vote/new")).andExpect(redirectedUrl("/vote/login?next=/vote/new"));
        MockHttpSession owner = signup("owner1");
        Long pollId = createPoll(owner, "カレー", "ラーメン");
        Long optionId = optionIds(pollId).get(0);
        mvc.perform(post("/vote/" + pollId + "/tickets").param("optionId", optionId.toString()).param("tickets", "5"))
                .andExpect(redirectedUrl("/vote/login?next=/vote/" + pollId));
        assertThat(votes.count()).isZero();
    }

    @Test
    void 券は何枚でも何回でも入れられ_足りなければ入れられない() throws Exception {
        MockHttpSession owner = signup("owner2");
        MockHttpSession voter = signup("voter2");
        Long pollId = createPoll(owner, "カレー", "ラーメン");
        List<Long> ids = optionIds(pollId);

        vote(voter, pollId, ids.get(0), 30);
        vote(voter, pollId, ids.get(0), 20);
        vote(voter, pollId, ids.get(1), 15);
        mvc.perform(post("/vote/" + pollId + "/tickets").session(voter).param("optionId", ids.get(1).toString()).param("tickets", "36"))
                .andExpect(redirectedUrl("/vote/" + pollId + "#options"));

        assertThat(users.findByLoginId("voter2").orElseThrow().getTotalTickets()).isEqualTo(35);
        mvc.perform(get("/vote/" + pollId).session(voter))
                .andExpect(content().string(containsString("<b>50</b>票")))
                .andExpect(content().string(containsString("<b>15</b>票")))
                .andExpect(content().string(containsString("総投票数 65票")));
    }

    @Test
    void 締め切ると1位にならなかった選択肢の券は全部戻り_1位の券は使われる() throws Exception {
        MockHttpSession owner = signup("owner3");
        MockHttpSession a = signup("voter_a");
        MockHttpSession b = signup("voter_b");
        Long pollId = createPoll(owner, "カレー", "ラーメン");
        List<Long> ids = optionIds(pollId);
        vote(a, pollId, ids.get(0), 50);   // カレー 50
        vote(a, pollId, ids.get(1), 15);   // ラーメン 15（保険）
        vote(b, pollId, ids.get(1), 40);   // ラーメン 計55 → 1位

        close(pollId);
        mvc.perform(get("/vote/" + pollId).session(a))
                .andExpect(content().string(containsString("ラーメン")))
                .andExpect(content().string(containsString("（1位にならなかったため、投票券は返却されました）")))
                .andExpect(content().string(containsString("（1位になったため、投票券は使用されました）")));
        // もう一度開いても、二重には返さない
        mvc.perform(get("/vote").session(a));

        assertThat(users.findByLoginId("voter_a").orElseThrow().getTotalTickets()).isEqualTo(100 - 15);
        assertThat(users.findByLoginId("voter_b").orElseThrow().getTotalTickets()).isEqualTo(100 - 40);
        Long aId = users.findByLoginId("voter_a").orElseThrow().getId();
        assertThat(ledger.findTop50ByUserIdOrderByCreatedAtDescIdDesc(aId))
                .extracting(TicketLedger::getReasonType)
                .containsOnlyOnce(TicketLedger.Reason.REFUND);
        assertThat(polls.findById(pollId).orElseThrow().getSettledAt()).isNotNull();
    }

    @Test
    void 同じ枚数で並んだら入れた人数が多いほうが1位() throws Exception {
        MockHttpSession owner = signup("owner4");
        MockHttpSession c = signup("voter_c");
        MockHttpSession d = signup("voter_d");
        MockHttpSession e = signup("voter_e");
        Long pollId = createPoll(owner, "右", "左");
        List<Long> ids = optionIds(pollId);
        vote(c, pollId, ids.get(0), 10);   // 右 30枚・2人
        vote(c, pollId, ids.get(0), 5);    // 同じ人が2回入れても1人
        vote(e, pollId, ids.get(0), 15);
        vote(d, pollId, ids.get(1), 30);   // 左 30枚・1人
        close(pollId);
        mvc.perform(get("/vote/" + pollId).session(d))
                .andExpect(content().string(containsString("1位（票数が同じため、投票した人数で決定）")))
                .andExpect(content().string(containsString("2人が投票")))
                .andExpect(content().string(containsString("（1位にならなかったため、投票券は返却されました）")));
        assertThat(users.findByLoginId("voter_c").orElseThrow().getTotalTickets()).isEqualTo(85);
        assertThat(users.findByLoginId("voter_e").orElseThrow().getTotalTickets()).isEqualTo(85);
        assertThat(users.findByLoginId("voter_d").orElseThrow().getTotalTickets()).isEqualTo(100);
        mvc.perform(get("/vote").param("tab", "closed"))
                .andExpect(content().string(containsString("1位：右")));
    }

    @Test
    void 枚数も人数も同じなら引き分けで_券は全部戻る() throws Exception {
        MockHttpSession owner = signup("owner6");
        MockHttpSession c = signup("voter_f");
        MockHttpSession d = signup("voter_g");
        MockHttpSession e = signup("voter_h");
        Long pollId = createPoll(owner, "右", "左", "まっすぐ");
        List<Long> ids = optionIds(pollId);
        vote(c, pollId, ids.get(0), 30);   // 右 30枚・1人
        vote(d, pollId, ids.get(1), 30);   // 左 30枚・1人
        vote(e, pollId, ids.get(2), 5);    // まっすぐ 5枚（1位ではないので、これも戻る）
        close(pollId);
        mvc.perform(get("/vote/" + pollId).session(c))
                .andExpect(content().string(containsString("引き分け")))
                .andExpect(content().string(containsString("（引き分けのため、投票券は返却されました）")))
                .andExpect(content().string(not(containsString("（1位になったため、投票券は使用されました）"))));
        for (String id : List.of("voter_f", "voter_g", "voter_h")) {
            assertThat(users.findByLoginId(id).orElseThrow().getTotalTickets()).isEqualTo(100);
        }
        Long cId = users.findByLoginId("voter_f").orElseThrow().getId();
        assertThat(ledger.findTop50ByUserIdOrderByCreatedAtDescIdDesc(cId))
                .extracting(TicketLedger::getReasonType)
                .containsOnlyOnce(TicketLedger.Reason.DRAW);
        mvc.perform(get("/vote/me").session(c))
                .andExpect(content().string(containsString("返却（引き分けのため）")));
        mvc.perform(get("/vote").param("tab", "closed"))
                .andExpect(content().string(containsString("class=\"winner-label\">引き分け</span>")));
        // 出した人は、引き分けでもどうしたかを報告できる
        mvc.perform(get("/vote/" + pollId + "/report").session(owner))
                .andExpect(content().string(containsString("引き分けでした")));
    }

    @Test
    void 一位の決め方() {
        // 券がいちばん多いほうが1位
        assertThat(PollSettlement.decide(List.of(tally(1, 50, 1), tally(2, 40, 9))))
                .isEqualTo(new PollSettlement.Outcome(1L, false, false));
        // 券が同じなら人数が多いほう
        assertThat(PollSettlement.decide(List.of(tally(1, 40, 2), tally(2, 40, 3), tally(3, 10, 8))))
                .isEqualTo(new PollSettlement.Outcome(2L, false, true));
        // 券も人数も同じなら引き分け（3つ並んでも同じ）
        assertThat(PollSettlement.decide(List.of(tally(1, 40, 2), tally(2, 40, 2), tally(3, 40, 1))))
                .isEqualTo(new PollSettlement.Outcome(null, true, false));
        // だれも入れていない
        assertThat(PollSettlement.decide(List.of())).isEqualTo(PollSettlement.Outcome.NO_VOTES);
    }

    private static OptionTally tally(long optionId, long tickets, long voters) {
        return new OptionTally() {
            public Long getPollId() { return 1L; }
            public Long getOptionId() { return optionId; }
            public Long getTickets() { return tickets; }
            public Long getVoters() { return voters; }
        };
    }

    @Test
    void 締め切ったあとは券を入れられない() throws Exception {
        MockHttpSession owner = signup("owner5");
        MockHttpSession voter = signup("voter5");
        Long pollId = createPoll(owner, "A", "B");
        close(pollId);
        mvc.perform(post("/vote/" + pollId + "/tickets").session(voter).param("optionId", optionIds(pollId).get(0).toString()).param("tickets", "5"))
                .andExpect(redirectedUrl("/vote/" + pollId + "#options"));
        assertThat(votes.count()).isZero();
        assertThat(users.findByLoginId("voter5").orElseThrow().getTotalTickets()).isEqualTo(100);
    }

    @Test
    void 報告できるのは出した人だけで_締め切りのあとに1回だけ() throws Exception {
        MockHttpSession owner = signup("owner6");
        MockHttpSession other = signup("other6");
        Long pollId = createPoll(owner, "髪を切る", "切らない");

        // 締め切り前は出せない
        mvc.perform(post("/vote/" + pollId + "/report").session(owner).param("text", "切りました"));
        assertThat(polls.findById(pollId).orElseThrow().isReported()).isFalse();

        close(pollId);
        // ほかの人は出せない
        mvc.perform(post("/vote/" + pollId + "/report").session(other).param("text", "なりすまし"));
        assertThat(polls.findById(pollId).orElseThrow().isReported()).isFalse();

        mvc.perform(post("/vote/" + pollId + "/report").session(owner).param("text", "切りました。さっぱり。"))
                .andExpect(redirectedUrl("/vote/" + pollId));
        mvc.perform(post("/vote/" + pollId + "/report").session(owner).param("text", "書き直し"));
        assertThat(polls.findById(pollId).orElseThrow().getReportText()).isEqualTo("切りました。さっぱり。");
        mvc.perform(get("/vote/" + pollId))
                .andExpect(content().string(containsString("実行報告")))
                .andExpect(content().string(containsString("切りました。さっぱり。")));
    }

    @Test
    void お題の中身がおかしければ出せない() throws Exception {
        MockHttpSession owner = signup("owner7");
        mvc.perform(post("/vote").session(owner).param("title", "どっち").param("option", "A", "", "", "").param("duration", "DAY_1"))
                .andExpect(content().string(containsString("選択肢は2〜4個にしてください。")));
        mvc.perform(post("/vote").session(owner).param("title", "どっち").param("option", "A", "A").param("duration", "DAY_1"))
                .andExpect(content().string(containsString("同じ選択肢が2つあります。")));
        mvc.perform(post("/vote").session(owner).param("title", " ").param("option", "A", "B").param("duration", "DAY_1"))
                .andExpect(content().string(containsString("タイトルは1〜60文字で入力してください。")));
        assertThat(polls.count()).isZero();
    }

    @Test
    void 管理画面でお題を消すと_入っていた券は全部戻る() throws Exception {
        MockHttpSession owner = signup("owner8");
        MockHttpSession voter = signup("voter8");
        Long pollId = createPoll(owner, "A", "B");
        vote(voter, pollId, optionIds(pollId).get(0), 40);
        List<Poll> deleted = pollService.deleteAll(List.of(pollId));
        assertThat(deleted).hasSize(1);
        assertThat(users.findByLoginId("voter8").orElseThrow().getTotalTickets()).isEqualTo(100);
        assertThat(polls.count()).isZero();
        assertThat(votes.count()).isZero();
        assertThat(options.count()).isZero();
    }

    @Test
    void よそのサイトから送られた投票は断る() throws Exception {
        MockHttpSession owner = signup("owner9");
        MockHttpSession voter = signup("voter9");
        Long pollId = createPoll(owner, "A", "B");
        mvc.perform(post("/vote/" + pollId + "/tickets").session(voter).header("Sec-Fetch-Site", "cross-site")
                        .param("optionId", optionIds(pollId).get(0).toString()).param("tickets", "100"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/vote/" + pollId + "/tickets").session(voter).header("Origin", "https://evil.example")
                        .param("optionId", optionIds(pollId).get(0).toString()).param("tickets", "100"))
                .andExpect(status().isForbidden());
        assertThat(votes.count()).isZero();
    }

    @Test
    void サーバーが作り直されてもログインのCookieでログインしたまま() throws Exception {
        MvcResult result = mvc.perform(post("/vote/signup").param("loginId", "keep_me").param("displayName", "けいぞく").param("password", "password123")
                        .with(r -> { r.setRemoteAddr(nextIp()); return r; }))
                .andReturn();
        Cookie login = result.getResponse().getCookie("vote_login");
        assertThat(login).isNotNull();
        assertThat(login.isHttpOnly()).isTrue();
        // セッションなし（作り直された直後）でも、Cookie だけでマイページが開ける
        mvc.perform(get("/vote/me").cookie(login))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("けいぞく さん")));
        // 署名を書き換えた Cookie は使えない
        mvc.perform(get("/vote/me").cookie(new Cookie("vote_login", login.getValue() + "x")))
                .andExpect(redirectedUrl("/vote/login?next=/vote/me"));
    }

    @Test
    void ログインのあとはよそのサイトへ飛ばない() {
        assertThat(AccountController.safeNext("/vote/12")).isEqualTo("/vote/12");
        assertThat(AccountController.safeNext("https://evil.example")).isEqualTo("/vote");
        assertThat(AccountController.safeNext("//evil.example")).isEqualTo("/vote");
        assertThat(AccountController.safeNext("/vote//evil.example")).isEqualTo("/vote");
        assertThat(AccountController.safeNext(null)).isEqualTo("/vote");
    }

    @Test
    void 一覧とサイトマップにお題が出る() throws Exception {
        MockHttpSession owner = signup("owner10");
        Long pollId = createPoll(owner, "朝はパン", "朝はごはん");
        mvc.perform(get("/vote"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("今日のお題")))
                .andExpect(content().string(containsString("朝はパン ／ 朝はごはん")));
        mvc.perform(get("/sitemap.xml"))
                .andExpect(content().string(containsString("<loc>https://web100tsukuru.com/vote</loc>")))
                .andExpect(content().string(containsString("<loc>https://web100tsukuru.com/vote/" + pollId + "</loc>")));
    }

    @Test
    void 券が入ると順位に出て_左の切り替えで受付中と報告ありに分かれる() throws Exception {
        MockHttpSession owner = signup("owner11");
        Long openId = createPoll(owner, "うどん", "そば");
        Long closedId = createPoll(owner, "海", "山");
        mvc.perform(get("/vote"))
                .andExpect(content().string(not(containsString("人気の投票"))));
        vote(owner, openId, optionIds(openId).get(0), 7);
        close(closedId);
        mvc.perform(get("/vote"))
                .andExpect(content().string(containsString("人気の投票")))
                .andExpect(content().string(containsString("class=\"poll-rank-no\">1</span>")));
        mvc.perform(get("/vote").param("tab", "open"))
                .andExpect(content().string(containsString("href=\"/vote?tab=open\" aria-current=\"page\"")))
                .andExpect(content().string(containsString("うどん ／ そば")))
                .andExpect(content().string(not(containsString("海 ／ 山"))));
        mvc.perform(get("/vote").param("tab", "reported"))
                .andExpect(content().string(containsString("実行報告がある投票は、まだありません。")))
                .andExpect(content().string(not(containsString("海 ／ 山"))));
        // 知らない切り替えの名前は「すべて」にする
        mvc.perform(get("/vote").param("tab", "<script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/vote\" aria-current=\"page\"")));
    }

    // ==========================================================

    private MockHttpSession signup(String loginId) throws Exception {
        MvcResult result = mvc.perform(post("/vote/signup")
                        .param("loginId", loginId).param("displayName", loginId + "さん").param("password", "password123")
                        .with(r -> { r.setRemoteAddr(nextIp()); return r; }))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long createPoll(MockHttpSession owner, String... labels) throws Exception {
        String location = mvc.perform(post("/vote").session(owner)
                        .param("title", "今日のお題").param("option", labels).param("duration", "DAY_1"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private void vote(MockHttpSession voter, Long pollId, Long optionId, long tickets) throws Exception {
        mvc.perform(post("/vote/" + pollId + "/tickets").session(voter)
                        .param("optionId", optionId.toString()).param("tickets", String.valueOf(tickets)))
                .andExpect(redirectedUrl("/vote/" + pollId + "#options"));
    }

    private List<Long> optionIds(Long pollId) {
        return options.findByPollIdOrderByOptionNo(pollId).stream().map(PollOption::getId).toList();
    }

    /** 締め切りを過ぎたことにする */
    private void close(Long pollId) {
        Poll poll = polls.findById(pollId).orElseThrow();
        poll.setClosesAt(LocalDateTime.now(Web100tsukuruApplication.ZONE).minusMinutes(1));
        polls.save(poll);
    }

    private static String nextIp() {
        return "10.20.0." + SEQ.incrementAndGet();
    }
}
