package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.AuctionLot;
import com.example.web100tsukuru.entity.AuctionLot.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionLotRepository extends JpaRepository<AuctionLot, Long> {

    /** 描いた人ごとの、絵が売れた額の合計と回数（転売も含む） */
    interface ArtistTotal {
        Long getArtistId();

        Long getTotal();

        Long getSales();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from AuctionLot l where l.id = :id")
    Optional<AuctionLot> lockById(@Param("id") Long id);

    // 終わりの時刻を過ぎて、まだ片づけていない競り
    @Query("select l.id from AuctionLot l where l.status = com.example.web100tsukuru.entity.AuctionLot.Status.OPEN "
            + "and l.endAt <= :now order by l.endAt")
    List<Long> idsDue(@Param("now") LocalDateTime now, Pageable pageable);

    // 開催中（終わりが近い順）
    List<AuctionLot> findByStatusAndEndAtAfterOrderByEndAtAscIdAsc(Status status, LocalDateTime now, Pageable pageable);

    // 落札された順
    List<AuctionLot> findByStatusOrderBySettledAtDescIdDesc(Status status, Pageable pageable);

    // その人が、いまいちばん高い値を付けている競りの上限額の合計（その分のコインは使えない）
    @Query("select coalesce(sum(l.topMax), 0) from AuctionLot l "
            + "where l.status = com.example.web100tsukuru.entity.AuctionLot.Status.OPEN and l.topUserId = :userId")
    long holdOf(@Param("userId") Long userId);

    List<AuctionLot> findByTopUserIdAndStatusOrderByEndAtAsc(Long topUserId, Status status);

    List<AuctionLot> findBySellerUserIdAndStatusOrderByEndAtAsc(Long sellerUserId, Status status);

    // 絵の来歴（売れた順）
    List<AuctionLot> findByArtworkIdAndStatusOrderBySettledAtAscIdAsc(Long artworkId, Status status);

    List<AuctionLot> findByArtworkId(Long artworkId);

    @Query("select count(l) from AuctionLot l where l.status = com.example.web100tsukuru.entity.AuctionLot.Status.OPEN "
            + "and l.sellerNpc is not null and l.endAt > :now")
    long countLiveByNpc(@Param("now") LocalDateTime now);

    @Query("select a.artistId as artistId, sum(l.price) as total, count(l) as sales from AuctionLot l, Artwork a "
            + "where l.artworkId = a.id and l.status = com.example.web100tsukuru.entity.AuctionLot.Status.SOLD "
            + "group by a.artistId order by sum(l.price) desc")
    List<ArtistTotal> totalsByArtist(Pageable pageable);

    @Query("select count(l) from AuctionLot l, Artwork a where l.artworkId = a.id and a.artistId = :artistId "
            + "and l.status = com.example.web100tsukuru.entity.AuctionLot.Status.SOLD")
    long salesOfArtist(@Param("artistId") Long artistId);
}
