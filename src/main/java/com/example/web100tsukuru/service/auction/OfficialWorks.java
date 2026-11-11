package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.repository.ArtworkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 公式（らくがきオークション公式）の絵。
 * 著作権の切れた名画を、このゲームの13色で「らくがき版」に描き直したもの
 * （絵は tools/official-art/paintings.mjs で描き、src/main/resources/official/ に置く）。
 * 起動したときに、まだ登録していない絵だけを公式のアカウント（OfficialAccount）の持ち物として登録する。
 * 競りには管理画面から出す。
 */
@Component
public class OfficialWorks implements ApplicationRunner {

    /** 公式の絵1枚（key はファイル名、credit は原画） */
    public record Work(String key, String title, String credit) {
    }

    public static final List<Work> WORKS = List.of(
            new Work("great-wave", "神奈川沖浪裏（らくがき版）", "葛飾北斎『冨嶽三十六景 神奈川沖浪裏』（1831年ごろ）"),
            new Work("red-fuji", "凱風快晴（らくがき版）", "葛飾北斎『冨嶽三十六景 凱風快晴』（1831年ごろ）"),
            new Work("starry-night", "星月夜（らくがき版）", "フィンセント・ファン・ゴッホ『星月夜』（1889年）"),
            new Work("sunflowers", "ひまわり（らくがき版）", "フィンセント・ファン・ゴッホ『ひまわり』（1888年）"),
            new Work("mona-lisa", "モナ・リザ（らくがき版）", "レオナルド・ダ・ヴィンチ『モナ・リザ』（1503〜1519年ごろ）"),
            new Work("pearl-earring", "真珠の耳飾りの少女（らくがき版）", "ヨハネス・フェルメール『真珠の耳飾りの少女』（1665年ごろ）"),
            new Work("scream", "叫び（らくがき版）", "エドヴァルド・ムンク『叫び』（1893年）"),
            new Work("impression-sunrise", "印象・日の出（らくがき版）", "クロード・モネ『印象・日の出』（1872年）"),
            new Work("water-lily-pond", "睡蓮の池（らくがき版）", "クロード・モネ『睡蓮の池』（1899年）"),
            new Work("the-kiss", "接吻（らくがき版）", "グスタフ・クリムト『接吻』（1908年）"),
            new Work("sharaku", "奴江戸兵衛（らくがき版）", "東洲斎写楽『三代目大谷鬼次の奴江戸兵衛』（1794年）"),
            new Work("gleaners", "落穂拾い（らくがき版）", "ジャン＝フランソワ・ミレー『落穂拾い』（1857年）"),
            new Work("pine-trees", "松林図屏風（らくがき版）", "長谷川等伯『松林図屏風』（16世紀末）")
    );

    private static final Logger log = LoggerFactory.getLogger(OfficialWorks.class);

    private final OfficialAccount account;
    private final ArtworkRepository artworks;
    private final ArtworkService artworkService;
    private final boolean enabled;

    public OfficialWorks(OfficialAccount account, ArtworkRepository artworks, ArtworkService artworkService,
                         @Value("${app.auction.official-works:true}") boolean enabled) {
        this.account = account;
        this.artworks = artworks;
        this.artworkService = artworkService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            load();
        } catch (RuntimeException e) {
            // 公式の絵が登録できなくても、サイトは動かす
            log.warn("公式の絵を登録できませんでした", e);
        }
    }

    /** まだ登録していない公式の絵を登録する。登録した枚数を返す */
    public int load() {
        Long id = account.ensure();
        int added = 0;
        for (Work w : WORKS) {
            if (artworks.existsByArtistIdAndTitle(id, w.title())) {
                continue;
            }
            try (InputStream in = new ClassPathResource("official/" + w.key() + ".png").getInputStream()) {
                artworkService.createOfficial(id, w.title(), w.credit(), in.readAllBytes());
                added++;
            } catch (IOException | ArtworkService.ArtworkException e) {
                log.warn("公式の絵 {} を登録できませんでした: {}", w.key(), e.getMessage());
            }
        }
        if (added > 0) {
            log.info("公式の絵を{}枚登録しました", added);
        }
        return added;
    }
}
