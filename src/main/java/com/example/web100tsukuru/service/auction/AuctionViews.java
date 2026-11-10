package com.example.web100tsukuru.service.auction;

import com.example.web100tsukuru.entity.AppUser;
import com.example.web100tsukuru.entity.Artwork;
import com.example.web100tsukuru.entity.AuctionBid;
import com.example.web100tsukuru.entity.AuctionLot;
import com.example.web100tsukuru.entity.AuctionLot.Status;
import com.example.web100tsukuru.entity.CoinWallet;
import com.example.web100tsukuru.repository.AppUserRepository;
import com.example.web100tsukuru.repository.ArtworkRepository;
import com.example.web100tsukuru.repository.ArtworkRepository.OwnerTotal;
import com.example.web100tsukuru.repository.AuctionBidRepository;
import com.example.web100tsukuru.repository.AuctionLotRepository;
import com.example.web100tsukuru.repository.AuctionLotRepository.ArtistTotal;
import com.example.web100tsukuru.repository.CoinWalletRepository;
import com.example.web100tsukuru.service.Avatars;
import com.example.web100tsukuru.service.auction.ArtCollectors.ArtCollector;
import com.example.web100tsukuru.service.auction.AuctionDay.Day;
import com.example.web100tsukuru.service.auction.Drawing.Features;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 画面に出すための組み立て（名前・アイコン・リンクを付ける） */
@Service
public class AuctionViews {

    // 人のアイコンの色（収集家はそれぞれ決まった色）
    private static final String[] USER_COLORS = {"#b5533c", "#3d7ea6", "#5b8c5a", "#b07d2b", "#7b5ea7", "#4f6d7a"};

    /** 名前とアイコンとリンク先（人か収集家） */
    public record Who(String name, String initial, String color, String href, boolean npc) {
    }

    public record WorkCard(Artwork work, Who artist, Who owner, AuctionLot lot) {
    }

    public record LotCard(AuctionLot lot, Artwork work, Who artist, Who seller, Who top) {
    }

    /** 入札の記録の1行。win は収集家が落札したときのひとこと */
    public record BidLine(Long id, Who who, long amount, LocalDateTime at, boolean auto, String line, boolean win) {
    }

    /** 来歴の1行（だれからだれへ、いくらで） */
    public record Sale(AuctionLot lot, Who from, Who to) {
    }

    public record Asset(Who who, long coins, long works, long worksValue) {
        public long total() {
            return coins + worksValue;
        }
    }

    public record ArtistRank(Who who, long total, long sales) {
    }

    /** 好みそうな収集家（来場しているか・流行の色に合うかも） */
    public record Fan(ArtCollector collector, boolean attendsToday, boolean attendsTomorrow) {
    }

    private final AppUserRepository users;
    private final ArtworkRepository artworks;
    private final AuctionLotRepository lots;
    private final AuctionBidRepository bids;
    private final CoinWalletRepository wallets;

    public AuctionViews(AppUserRepository users, ArtworkRepository artworks, AuctionLotRepository lots,
                        AuctionBidRepository bids, CoinWalletRepository wallets) {
        this.users = users;
        this.artworks = artworks;
        this.lots = lots;
        this.bids = bids;
        this.wallets = wallets;
    }

    public static Who npc(String key) {
        ArtCollector c = ArtCollectors.find(key).orElse(null);
        if (c == null) {
            return new Who("収集家", "収", "#8c8c8c", "/auction/collectors", true);
        }
        return new Who(c.name(), c.initial(), c.color(), "/auction/collectors/" + c.key(), true);
    }

    public static Who user(Long id, String name) {
        String n = name == null ? "退会した人" : name;
        return new Who(n, Avatars.initial(n), USER_COLORS[Avatars.color(n)], "/auction/users/" + id, false);
    }

    private Map<Long, Who> people(Collection<Long> ids) {
        Set<Long> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Who> result = new HashMap<>();
        for (AppUser u : users.findAllById(wanted)) {
            result.put(u.getId(), user(u.getId(), u.getDisplayName()));
        }
        for (Long id : wanted) {
            result.putIfAbsent(id, user(id, null));
        }
        return result;
    }

    private static Who either(Map<Long, Who> people, Long userId, String npc) {
        if (userId != null) {
            return people.get(userId);
        }
        return npc != null ? npc(npc) : null;
    }

    @Transactional(readOnly = true)
    public Who who(Long userId) {
        return people(List.of(userId)).get(userId);
    }

    @Transactional(readOnly = true)
    public List<WorkCard> cards(List<Artwork> list) {
        Set<Long> ids = new HashSet<>();
        Set<Long> lotIds = new HashSet<>();
        for (Artwork a : list) {
            ids.add(a.getArtistId());
            ids.add(a.getOwnerUserId());
            if (a.getOpenLotId() != null) {
                lotIds.add(a.getOpenLotId());
            }
        }
        Map<Long, Who> people = people(ids);
        Map<Long, AuctionLot> open = lots.findAllById(lotIds).stream()
                .collect(Collectors.toMap(AuctionLot::getId, Function.identity()));
        return list.stream().map(a -> new WorkCard(a, people.get(a.getArtistId()),
                either(people, a.getOwnerUserId(), a.getOwnerNpc()), open.get(a.getOpenLotId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<LotCard> lotCards(List<AuctionLot> list) {
        Map<Long, Artwork> works = artworks.findAllById(list.stream().map(AuctionLot::getArtworkId).toList()).stream()
                .collect(Collectors.toMap(Artwork::getId, Function.identity()));
        Set<Long> ids = new HashSet<>();
        for (AuctionLot l : list) {
            ids.add(l.getSellerUserId());
            ids.add(l.getTopUserId());
            Artwork a = works.get(l.getArtworkId());
            if (a != null) {
                ids.add(a.getArtistId());
            }
        }
        Map<Long, Who> people = people(ids);
        List<LotCard> result = new ArrayList<>();
        for (AuctionLot l : list) {
            Artwork a = works.get(l.getArtworkId());
            if (a == null) {
                continue;
            }
            result.add(new LotCard(l, a, people.get(a.getArtistId()), either(people, l.getSellerUserId(), l.getSellerNpc()),
                    either(people, l.getTopUserId(), l.getTopNpc())));
        }
        return result;
    }

    /** 開催中の競り（終わりが近い順） */
    @Transactional(readOnly = true)
    public List<LotCard> live(int limit) {
        return lotCards(lots.findByStatusAndEndAtAfterOrderByEndAtAscIdAsc(Status.OPEN, AuctionEngine.now(), PageRequest.of(0, limit)));
    }

    /** 最近落札された競り */
    @Transactional(readOnly = true)
    public List<LotCard> recentSales(int limit) {
        return lotCards(lots.findByStatusOrderBySettledAtDescIdDesc(Status.SOLD, PageRequest.of(0, limit)));
    }

    /** 高く売れた絵（名画） */
    @Transactional(readOnly = true)
    public List<WorkCard> masterpieces(int limit) {
        return cards(artworks.findByLastPriceGreaterThanOrderByLastPriceDescIdDesc(0, PageRequest.of(0, limit)));
    }

    /** 報告のあった絵（報告の多い順） */
    @Transactional(readOnly = true)
    public List<WorkCard> reported() {
        return cards(artworks.findByReportCountGreaterThanOrderByReportCountDescIdDesc(0));
    }

    @Transactional(readOnly = true)
    public List<WorkCard> newest(int limit) {
        return cards(artworks.findAllByOrderByIdDesc(PageRequest.of(0, limit)));
    }

    @Transactional(readOnly = true)
    public List<WorkCard> ownedBy(Long userId) {
        return cards(artworks.findByOwnerUserIdOrderByIdDesc(userId));
    }

    @Transactional(readOnly = true)
    public List<WorkCard> drawnBy(Long userId) {
        return cards(artworks.findByArtistIdOrderByIdDesc(userId));
    }

    @Transactional(readOnly = true)
    public List<WorkCard> ownedByCollector(String key) {
        return cards(artworks.findByOwnerNpcOrderByLastPriceDescIdDesc(key));
    }

    /** その人がいちばん高い値を付けている競り */
    @Transactional(readOnly = true)
    public List<LotCard> leading(Long userId) {
        return lotCards(lots.findByTopUserIdAndStatusOrderByEndAtAsc(userId, Status.OPEN));
    }

    /** その人が出品中の競り */
    @Transactional(readOnly = true)
    public List<LotCard> selling(Long userId) {
        return lotCards(lots.findBySellerUserIdAndStatusOrderByEndAtAsc(userId, Status.OPEN));
    }

    /** 入札の記録（新しい順） */
    @Transactional(readOnly = true)
    public List<BidLine> bidLines(Long lotId) {
        List<AuctionBid> list = bids.findTop40ByLotIdOrderByIdDesc(lotId);
        Map<Long, Who> people = people(list.stream().map(AuctionBid::getUserId).toList());
        return list.stream().map(b -> new BidLine(b.getId(), either(people, b.getUserId(), b.getNpc()), b.getAmount(),
                b.getBidAt(), b.isAuto(), b.getLine(), b.isAuto() && b.getNpc() != null)).toList();
    }

    /** 絵の来歴（新しい順） */
    @Transactional(readOnly = true)
    public List<Sale> provenance(Long artworkId) {
        List<AuctionLot> sold = lots.findByArtworkIdAndStatusOrderBySettledAtAscIdAsc(artworkId, Status.SOLD);
        Set<Long> ids = new HashSet<>();
        for (AuctionLot l : sold) {
            ids.add(l.getSellerUserId());
            ids.add(l.getTopUserId());
        }
        Map<Long, Who> people = people(ids);
        List<Sale> result = new ArrayList<>(sold.stream().map(l -> new Sale(l, either(people, l.getSellerUserId(), l.getSellerNpc()),
                either(people, l.getTopUserId(), l.getTopNpc()))).toList());
        java.util.Collections.reverse(result);
        return result;
    }

    /** 資産（コイン＋持っている絵の値打ち）の多い順 */
    @Transactional(readOnly = true)
    public List<Asset> assets(int limit) {
        Map<Long, OwnerTotal> works = artworks.totalsByOwner().stream()
                .collect(Collectors.toMap(OwnerTotal::getUserId, Function.identity()));
        List<CoinWallet> all = wallets.findAll();
        Map<Long, Who> people = people(all.stream().map(CoinWallet::getUserId).toList());
        return all.stream().map(w -> {
                    OwnerTotal t = works.get(w.getUserId());
                    return new Asset(people.get(w.getUserId()), w.getCoins(), t == null ? 0 : t.getWorks(), t == null ? 0 : t.getTotal());
                })
                .sorted(Comparator.comparingLong(Asset::total).reversed())
                .limit(limit)
                .toList();
    }

    /** 描いた絵が売れた額の合計の多い順（転売も含む） */
    @Transactional(readOnly = true)
    public List<ArtistRank> artists(int limit) {
        List<ArtistTotal> totals = lots.totalsByArtist(PageRequest.of(0, limit));
        Map<Long, Who> people = people(totals.stream().map(ArtistTotal::getArtistId).toList());
        return totals.stream().map(t -> new ArtistRank(people.get(t.getArtistId()), t.getTotal(), t.getSales())).toList();
    }

    /** この絵を好みそうな収集家（好みの強い順） */
    public static List<Fan> fans(Artwork a, Day today, Day tomorrow) {
        Features f = Appraisal.features(a);
        return ArtCollectors.ALL.stream()
                .filter(c -> c.tasteFor(f) >= Appraisal.LIKES)
                .sorted(Comparator.comparingDouble((ArtCollector c) -> c.tasteFor(f) * c.wealth()).reversed())
                .map(c -> new Fan(c, today.attends(c.key()), tomorrow.attends(c.key())))
                .toList();
    }

    /** 流行の色が、この絵でどのくらい使われているか（0〜1、0.4 で最大） */
    public static double trendMatch(Artwork a, Day day) {
        return Math.min(1, Appraisal.features(a).shareOf(day.trend().colors()) / 0.4);
    }
}
