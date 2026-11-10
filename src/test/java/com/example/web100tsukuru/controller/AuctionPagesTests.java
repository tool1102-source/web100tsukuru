package com.example.web100tsukuru.controller;

import com.example.web100tsukuru.repository.ArtworkImageRepository;
import com.example.web100tsukuru.repository.ArtworkReportRepository;
import com.example.web100tsukuru.repository.ArtworkRepository;
import com.example.web100tsukuru.repository.AuctionBidRepository;
import com.example.web100tsukuru.repository.AuctionLotRepository;
import com.example.web100tsukuru.repository.CoinLedgerRepository;
import com.example.web100tsukuru.repository.CoinWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 「らくがきオークション」の画面を、利用者と同じ順に開いて確かめる */
@SpringBootTest
@AutoConfigureMockMvc
class AuctionPagesTests {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ArtworkRepository artworks;
    @Autowired ArtworkImageRepository images;
    @Autowired ArtworkReportRepository reports;
    @Autowired AuctionLotRepository lots;
    @Autowired AuctionBidRepository bids;
    @Autowired CoinWalletRepository wallets;
    @Autowired CoinLedgerRepository ledger;

    @BeforeEach
    void clean() {
        bids.deleteAll();
        lots.deleteAll();
        reports.deleteAll();
        images.deleteAll();
        artworks.deleteAll();
        ledger.deleteAll();
        wallets.deleteAll();
    }

    private MockHttpSession signup(String name) throws Exception {
        MvcResult result = mvc.perform(post("/auction/signup")
                        .param("loginId", "ap_" + SEQ.incrementAndGet()).param("displayName", name).param("password", "password123")
                        .param("next", "/auction/draw")
                        .with(r -> { r.setRemoteAddr("10.40.0." + SEQ.incrementAndGet()); return r; }))
                .andExpect(redirectedUrl("/auction/draw"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static byte[] drawing() throws Exception {
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 512, 512);
        g.setColor(new Color(0x3fa34d));
        g.fillOval(100, 100, 300, 300);
        g.setColor(new Color(0x222222));
        g.fillRect(240, 380, 30, 120);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private String save(MockHttpSession session, String title) throws Exception {
        String body = mvc.perform(multipart("/auction/artworks")
                        .file(new MockMultipartFile("image", "drawing.png", "image/png", drawing()))
                        .param("title", title).header("X-Requested-With", "fetch").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"url\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void はじめての人が登録して描いて出品し_競りの画面を開ける() throws Exception {
        // 登録前：トップは見られて、描くにはログインが要る
        mvc.perform(get("/auction"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"site-auction\"")))
                .andExpect(content().string(containsString("描いた絵が、")))
                .andExpect(content().string(containsString("No.004 / 100")))
                .andExpect(content().string(containsString("family=Zen+Old+Mincho")))
                .andExpect(content().string(containsString("/css/auction")))
                .andExpect(content().string(containsString("<meta property=\"og:image\" content=\"https://web100tsukuru.com/img/og/auction.jpg\">")))
                .andExpect(content().string(containsString("いま開催中の競りはありません")));
        mvc.perform(get("/auction/draw")).andExpect(redirectedUrl("/auction/login?next=/auction/draw"));

        MockHttpSession me = signup("はじめての画家");
        mvc.perform(get("/auction/draw").session(me).flashAttr("notice", "登録が完了しました。"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("はじめての参加なので、コインを1,000枚受け取りました。")))
                .andExpect(content().string(containsString("今日の収集家の反応")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString("1,000")));

        String url = save(me, "まるい木");
        assertThat(url).matches("/auction/artworks/\\d+");
        mvc.perform(get(url).session(me))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("まるい木")))
                .andExpect(content().string(containsString("この絵を競りに出す")))
                .andExpect(content().string(containsString("この絵を好みそうな収集家")))
                .andExpect(content().string(containsString("森野さん")))
                .andExpect(content().string(containsString("og.jpg")));
        // ほかの人には出品の欄は出ない
        mvc.perform(get(url)).andExpect(content().string(not(containsString("この絵を競りに出す"))));

        mvc.perform(get(url + ".png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        byte[] og = mvc.perform(get(url + "/og.jpg")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage ogImage = ImageIO.read(new ByteArrayInputStream(og));
        assertThat(ogImage.getWidth()).isEqualTo(1200);
        assertThat(ogImage.getHeight()).isEqualTo(630);

        String lotUrl = mvc.perform(post(url + "/sell").session(me).param("startPrice", "10").param("length", "MINUTES_3"))
                .andExpect(redirectedUrlPattern("/auction/lots/*"))
                .andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(lotUrl).session(me))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3分の競り")))
                .andExpect(content().string(containsString("あなたが出品した絵です")))
                .andExpect(content().string(containsString("この日の来場者")));
        mvc.perform(get(lotUrl + "/state").session(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.me.seller").value(true))
                .andExpect(jsonPath("$.me.coins").value(1000));
        // 出品中は、トップの「開催中の競り」に出る
        mvc.perform(get("/auction")).andExpect(content().string(containsString("まるい木")));
        mvc.perform(get("/auction/me").session(me))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("出品中")))
                .andExpect(content().string(containsString("はじめての参加")));
    }

    @Test
    void ほかの人の競りに入札できる_ログインしていなければログインへ() throws Exception {
        MockHttpSession artist = signup("描く人");
        String url = save(artist, "入札される絵");
        String lotUrl = mvc.perform(post(url + "/sell").session(artist).param("startPrice", "10").param("length", "DAY_1"))
                .andReturn().getResponse().getRedirectedUrl();

        mvc.perform(post(lotUrl + "/bid").param("amount", "500")).andExpect(redirectedUrl("/auction/login?next=" + lotUrl));
        mvc.perform(post(lotUrl + "/bid").param("amount", "500").header("X-Requested-With", "fetch"))
                .andExpect(status().isUnauthorized());

        MockHttpSession buyer = signup("買う人");
        mvc.perform(get(lotUrl).session(buyer))
                .andExpect(content().string(containsString("上限額を決めて入札")))
                .andExpect(content().string(containsString("1日の競り")));
        mvc.perform(post(lotUrl + "/bid").session(buyer).param("amount", "999999").header("X-Requested-With", "fetch"))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.outcome").value("NOT_ENOUGH"));
        mvc.perform(post(lotUrl + "/bid").session(buyer).param("amount", "900").header("X-Requested-With", "fetch"))
                .andExpect(jsonPath("$.ok").value(true));
        mvc.perform(get(lotUrl + "/state").session(buyer))
                .andExpect(jsonPath("$.me.top").value(true))
                .andExpect(jsonPath("$.me.max").value(900))
                .andExpect(jsonPath("$.me.available").value(100));
        // 出品した人は入札できない（フォームから送っても、画面にエラーが出る）
        mvc.perform(post(lotUrl + "/bid").session(artist).param("amount", "2000"))
                .andExpect(redirectedUrl(lotUrl));
        mvc.perform(get("/auction/me").session(buyer))
                .andExpect(content().string(containsString("いちばん高い値を付けている競り")))
                .andExpect(content().string(containsString("900 枚")));
    }

    @Test
    void 保存はログインと絵が要る() throws Exception {
        mvc.perform(multipart("/auction/artworks").file(new MockMultipartFile("image", "d.png", "image/png", drawing()))
                        .param("title", "x").header("X-Requested-With", "fetch"))
                .andExpect(status().isUnauthorized());
        MockHttpSession me = signup("試す人");
        mvc.perform(multipart("/auction/artworks").file(new MockMultipartFile("image", "d.txt", "text/plain", "abc".getBytes()))
                        .param("title", "x").header("X-Requested-With", "fetch").session(me))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("読み取れません")));
    }

    @Test
    void 収集家_ランキング_遊び方_人のページが開ける() throws Exception {
        MockHttpSession me = signup("見る人");
        save(me, "見られる絵");
        mvc.perform(get("/auction/collectors"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("赤坂伯爵")))
                .andExpect(content().string(containsString("ひまりちゃん")))
                .andExpect(content().string(containsString("実在の人物ではありません")));
        mvc.perform(get("/auction/collectors/sumida"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("墨田画伯")));
        mvc.perform(get("/auction/collectors/nobody")).andExpect(status().isNotFound());
        mvc.perform(get("/auction/ranking"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("見る人")));
        mvc.perform(get("/auction/rules"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("お金やほかの物と交換できません")))
                .andExpect(content().string(containsString("1,000")));
        String userUrl = mvc.perform(get("/auction/ranking")).andReturn().getResponse().getContentAsString()
                .replaceAll("(?s).*href=\"(/auction/users/\\d+)\".*", "$1");
        mvc.perform(get(userUrl))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("見られる絵")));
        mvc.perform(get("/auction/users/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/auction/artworks/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/auction/lots/999999")).andExpect(status().isNotFound());
    }

    @Test
    void ログインと登録の画面はオークションの見た目で_戻る先はオークションの中だけ() throws Exception {
        mvc.perform(get("/auction/login").param("next", "/auction/draw"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"site-auction\"")))
                .andExpect(content().string(containsString("action=\"/auction/login\"")))
                .andExpect(content().string(containsString("action=\"/auction/login/google\"")))
                .andExpect(content().string(containsString("value=\"/auction/draw\"")));
        mvc.perform(get("/auction/signup"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("登録してコインをもらう")));
        assertThat(AccountController.safeNext("/auction/lots/3", "/auction")).isEqualTo("/auction/lots/3");
        assertThat(AccountController.safeNext("/vote/3", "/auction")).isEqualTo("/auction");
        assertThat(AccountController.safeNext("https://evil.example", "/auction")).isEqualTo("/auction");
        assertThat(AccountController.safeNext("/auction//evil.example", "/auction")).isEqualTo("/auction");
        // ログアウトするとオークションのトップへ
        MockHttpSession me = signup("出ていく人");
        mvc.perform(post("/auction/logout").session(me)).andExpect(redirectedUrl("/auction"));
    }
}
