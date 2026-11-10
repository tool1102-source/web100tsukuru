package com.example.web100tsukuru.repository;

import com.example.web100tsukuru.entity.Artwork;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArtworkRepository extends JpaRepository<Artwork, Long> {

    /** 持ち主ごとの、持っている絵の値打ち（最後に売れた値段）の合計 */
    interface OwnerTotal {
        Long getUserId();

        Long getTotal();

        Long getWorks();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artwork a where a.id = :id")
    Optional<Artwork> lockById(@Param("id") Long id);

    List<Artwork> findByOwnerUserIdOrderByIdDesc(Long ownerUserId);

    List<Artwork> findByArtistIdOrderByIdDesc(Long artistId);

    long countByArtistIdAndCreatedAtGreaterThanEqual(Long artistId, LocalDateTime since);

    List<Artwork> findByOwnerNpcOrderByLastPriceDescIdDesc(String ownerNpc);

    // 新しく描かれた順
    List<Artwork> findAllByOrderByIdDesc(Pageable pageable);

    // 高く売れた順（名画）
    List<Artwork> findByLastPriceGreaterThanOrderByLastPriceDescIdDesc(long min, Pageable pageable);

    // 収集家が持っていて、出し直す時刻を過ぎたもの
    @Query("select a from Artwork a where a.ownerNpc is not null and a.openLotId is null and a.relistAt <= :now order by a.relistAt")
    List<Artwork> dueForNpcRelist(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("select a.ownerUserId as userId, sum(a.lastPrice) as total, count(a) as works from Artwork a "
            + "where a.ownerUserId is not null group by a.ownerUserId")
    List<OwnerTotal> totalsByOwner();

    List<Artwork> findByReportCountGreaterThanOrderByReportCountDescIdDesc(int min);
}
